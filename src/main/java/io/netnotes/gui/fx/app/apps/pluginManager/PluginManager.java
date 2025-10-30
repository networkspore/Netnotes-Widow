package io.netnotes.gui.fx.app.apps.pluginManager;

import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.plugins.OSGiPluginInformation;
import io.netnotes.engine.plugins.OSGiPluginMetaData;
import io.netnotes.engine.plugins.OSGiPluginRegistry;
import io.netnotes.engine.plugins.OSGiPluginRelease;
import io.netnotes.engine.plugins.OSGiUpdateLoader;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.StreamUtils.StreamProgressTracker;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.contentManager.AppBox;
import io.netnotes.gui.fx.display.contentManager.AppManagerInterface;
import io.netnotes.gui.fx.display.contentManager.IApp;
import io.netnotes.gui.fx.display.contentManager.SideBarButton;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.scene.image.Image;

/**
 * Plugin Manager - Manages plugin metadata, downloads, and persistence.
 * Does NOT handle OSGi bundle loading - only manages the plugin registry and downloads.
 */
public class PluginManager implements IApp {
    
    private static final String APP_NAME = "Plugin Manager";
    private final NoteBytesReadOnly m_appId;
    
    private AppDataInterface m_appData;
    private AppManagerInterface m_tabManager;
    
    private SideBarButton m_sideBarButton;
    private GitHubInfo m_gitHubInfo;
    
    private Map<NoteBytes, AppBox> m_openAppBoxes = new ConcurrentHashMap<>();
    
    // Plugin management components
    private OSGiPluginRegistry m_pluginRegistry;
    private OSGiUpdateLoader m_updateLoader;
    private PluginGroupManager m_groupManager;

    public PluginManager() {
        m_appId = new NoteBytesReadOnly(new NoteBytes("PluginManager"));
        m_gitHubInfo = FxResourceFactory.GITHUB_PROJECT_INFO;

        Image appIcon = new Image(FxResourceFactory.WIDOW120);
        m_sideBarButton = new SideBarButton(appIcon, APP_NAME);
        m_sideBarButton.setOnAction(_ -> openMainTab());
    }
    
    @Override
    public NoteBytesReadOnly getAppId() {
        return m_appId;
    }
    
    @Override
    public String getName() {
        return APP_NAME;
    }
    
    @Override
    public void init(AppDataInterface appDataInterface, AppManagerInterface tabManagerInterface) {
        m_appData = appDataInterface;
        m_tabManager = tabManagerInterface;
        m_pluginRegistry = new OSGiPluginRegistry(m_appData.getExecService());
        m_updateLoader = new OSGiUpdateLoader(m_gitHubInfo, "plugins.json", m_appData.getExecService());
        m_groupManager = new PluginGroupManager();
 
        // Initialize registry
        m_appData.getNoteFile(OSGiPluginRegistry.PLUGINS_REGISTRY_PATH).thenAccept(noteFile ->
            m_pluginRegistry.initialize(noteFile)
                .thenAccept(_ -> {
                    System.out.println("Plugin registry initialized with " + 
                        m_pluginRegistry.getInstalledPlugins().size() + " plugins");
                })
                .exceptionally(error -> {
                    System.err.println("Error initializing plugin registry: " + error.getMessage());
                    error.printStackTrace();
                    return null;
                }));
    }
    
    @Override
    public SideBarButton getSideBarButton() {
        return m_sideBarButton;
    }
    
    /**
     * Get the AppDataInterface for accessing NoteFiles.
     */
    public AppDataInterface getAppData() {
        return m_appData;
    }
    
    /**
     * Open or focus the main Plugin Manager tab.
     */
    private void openMainTab() {
        NoteBytes tabId = PluginManagerBox.ID;
        
        if (!m_tabManager.containsTab(tabId)) {
            PluginManagerBox managerBox = new PluginManagerBox(
                m_tabManager.getStage(), 
                m_gitHubInfo
            );
            
            // Pass plugin manager instance to UI
            managerBox.setPluginManager(this);
            
            m_tabManager.addTab(tabId, APP_NAME, managerBox);
        } else {
            m_tabManager.setCurrentTab(tabId);
        }
    }
    
    /**
     * Get the plugin group manager for UI operations.
     */
    public PluginGroupManager getGroupManager() {
        return m_groupManager;
    }
    
    /**
     * Get the plugin registry.
     */
    public OSGiPluginRegistry getRegistry() {
        return m_pluginRegistry;
    }
    
    /**
     * Get the update loader for fetching available plugins.
     */
    public OSGiUpdateLoader getUpdateLoader() {
        return m_updateLoader;
    }
    
    /**
     * Load available plugins from GitHub and update the group manager.
     */
    public CompletableFuture<List<OSGiPluginInformation>> loadAvailablePlugins() {
        return m_updateLoader.loadAvailableApps()
            .thenApply(availableApps -> {
                m_groupManager.buildFromRegistry(m_pluginRegistry, availableApps);
                System.out.println("Loaded " + availableApps.size() + " available plugins");
                return availableApps;
            });
    }
    
    /**
     * Install a plugin from a GitHub release.
     * Downloads the JAR to a NoteFile and registers it in the plugin registry.
     */
    public CompletableFuture<OSGiPluginMetaData> installPlugin(
        OSGiPluginRelease release, 
        boolean enabled, 
        StreamProgressTracker tracker
    ) {
        System.out.println("Starting installation of: " + release.getPluginInfo().getName() + 
            " version " + release.getTagName());
     
     
        return release.getPluginNoteFile(m_appData).thenCompose(noteFile -> {
                return downloadToNoteFile(release.getDownloadUrl(), noteFile, tracker)
                    .thenApply(_ -> {
                        // Create metadata and register
                        OSGiPluginMetaData metadata = new OSGiPluginMetaData(release, enabled);
                        return metadata;
                    });
            })
            .thenCompose(metadata -> 
                m_pluginRegistry.registerPlugin(metadata)
                    .thenApply(_ -> metadata)
            )
            .thenApply(metadata -> {
                // Update group manager
                m_updateLoader.loadAvailableApps()
                    .thenAccept(apps -> m_groupManager.buildFromRegistry(m_pluginRegistry, apps));
                return metadata;
            });
    }
    
    /**
     * Download a file from URL to a NoteFile.
     */
    private CompletableFuture<NoteBytesObject> downloadToNoteFile(
        String downloadUrl, 
        NoteFile noteFile,
        StreamProgressTracker progressTracker
    ) {
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> downloadFuture = UrlStreamHelpers.transferUrlStream(
            downloadUrl, outputStream, progressTracker, TaskUtils.getVirtualExecutor()
        );
        
        CompletableFuture<NoteBytesObject> writeFuture = noteFile.writeOnly(outputStream);
        
        return CompletableFuture.allOf(downloadFuture, writeFuture)
            .thenCompose(v -> writeFuture);
    }
    
    /**
     * Uninstall a plugin by removing it from the registry and deleting its NoteFile.
     */
    public CompletableFuture<Void> uninstallPlugin(String pluginId) {
        OSGiPluginMetaData metadata = m_pluginRegistry.getPlugin(pluginId);
        if (metadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Plugin not found: " + pluginId)
            );
        }
        
        System.out.println("Uninstalling plugin: " + pluginId);
        
        // Unregister from registry
        return m_pluginRegistry.unregisterPlugin(pluginId)
            .thenCompose(_ -> m_appData.deleteNoteFilePath(metadata.getPluginNotePath(), false, null))
            .thenRun(() -> {
                // Update group manager
                m_updateLoader.loadAvailableApps()
                    .thenAccept(apps -> m_groupManager.buildFromRegistry(m_pluginRegistry, apps));
                System.out.println("Plugin uninstalled: " + pluginId);
            });
    }
    
    /**
     * Enable a plugin (mark as enabled in registry).
     * Only one version of the same app can be enabled at a time.
     */
    public CompletableFuture<Void> enablePlugin(String pluginId) {
        return m_pluginRegistry.setPluginEnabled(pluginId, true)
            .thenRun(() -> {
                // Update group manager
                m_updateLoader.loadAvailableApps()
                    .thenAccept(apps -> m_groupManager.buildFromRegistry(m_pluginRegistry, apps));
                System.out.println("Plugin enabled: " + pluginId);
            });
    }
    
    /**
     * Disable a plugin (mark as disabled in registry).
     */
    public CompletableFuture<Void> disablePlugin(String pluginId) {
        return m_pluginRegistry.setPluginEnabled(pluginId, false)
            .thenRun(() -> {
                // Update group manager
                m_updateLoader.loadAvailableApps()
                    .thenAccept(apps -> m_groupManager.buildFromRegistry(m_pluginRegistry, apps));
                System.out.println("Plugin disabled: " + pluginId);
            });
    }
    
    /**
     * Get all installed plugins.
     */
    public List<OSGiPluginMetaData> getInstalledPlugins() {
        return m_pluginRegistry.getAllPlugins();
    }
    
    /**
     * Check if a plugin is installed.
     */
    public boolean isPluginInstalled(String pluginId) {
        return m_pluginRegistry.isPluginInstalled(pluginId);
    }
    
    /**
     * Shutdown - close all resources.
     */
    public CompletableFuture<Void> shutdown() {
        // Close all open app boxes
        for (AppBox appBox : m_openAppBoxes.values()) {
            appBox.shutdown();
        }
        m_openAppBoxes.clear();
        
        // Shutdown registry
        if (m_pluginRegistry != null) {
            m_pluginRegistry.shutdown();
        }
        
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }
}