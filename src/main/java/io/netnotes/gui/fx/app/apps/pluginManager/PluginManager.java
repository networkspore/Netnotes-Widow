package io.netnotes.gui.fx.app.apps.pluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.plugins.OSGiPluginDownloader;
import io.netnotes.engine.plugins.OSGiPluginFactory;
import io.netnotes.engine.plugins.OSGiPluginRegistry;
import io.netnotes.engine.plugins.OSGiPluginRelease;
import io.netnotes.engine.plugins.PluginMetaData;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.StreamUtils.StreamProgressTracker;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.contentManager.AppBox;
import io.netnotes.gui.fx.display.contentManager.AppManagerInterface;
import io.netnotes.gui.fx.display.contentManager.IApp;
import io.netnotes.gui.fx.display.contentManager.SideBarButton;
import javafx.scene.image.Image;

/**
 * Enhanced PluginManager that handles:
 * - Downloading plugins from GitHub
 * - Installing plugins to NoteFiles
 * - Managing plugin registry
 * - Loading plugins via OSGi
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
    private OSGiPluginDownloader m_pluginDownloader;
    private OSGiPluginFactory m_osgiFactory;
    
    // Track loaded plugin bundles
    private Map<NoteBytes, org.osgi.framework.Bundle> m_loadedBundles = new ConcurrentHashMap<>();

    public PluginManager() {
        m_appId = new NoteBytesReadOnly(new NoteBytes("PluginManager"));
        m_gitHubInfo = FxResourceFactory.GITHUB_PROJECT_INFO;
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
        
        // Initialize plugin management components
        m_pluginRegistry = new OSGiPluginRegistry(m_appData, m_appData.getExecService());
        m_pluginDownloader = new OSGiPluginDownloader(m_appData, m_appData.getExecService());
        m_osgiFactory = new OSGiPluginFactory(m_appData);
        
        // Initialize registry and load enabled plugins
        m_pluginRegistry.initialize()
            .thenCompose(v -> loadEnabledPlugins())
            .thenAccept(bundles -> {
                m_loadedBundles.putAll(bundles);
                System.out.println("Loaded " + bundles.size() + " plugins at startup");
            })
            .exceptionally(error -> {
                System.err.println("Error loading plugins: " + error.getMessage());
                error.printStackTrace();
                return null;
            });
        
        // Create sidebar button
        Image appIcon = new Image(FxResourceFactory.WIDOW120);
        m_sideBarButton = new SideBarButton(appIcon, APP_NAME);
        m_sideBarButton.setOnAction(_ -> openMainTab());
    }
    
    @Override
    public SideBarButton getSideBarButton() {
        return m_sideBarButton;
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
            
            // Pass plugin manager instance to UI for operations
            managerBox.setPluginManager(this);
            
            m_tabManager.addTab(tabId, APP_NAME, managerBox);
        } else {
            m_tabManager.setCurrentTab(tabId);
        }
    }
    
    /**
     * Install a plugin from a GitHub release.
     */
    public CompletableFuture<PluginMetaData> installPlugin(OSGiPluginRelease release, StreamProgressTracker tracker) {
        System.out.println("Starting installation of: " + release.getPluginInfo().getName());
        
        return m_pluginDownloader.downloadAndInstall(release, tracker)
            .thenCompose(installResult -> {
                // Create plugin metadata
                PluginMetaData metadata = new PluginMetaData(
                    installResult.getPluginId(),
                    installResult.getVersion(),
                    true, // Enabled by default
                    installResult.getJarPath()
                );
                
                // Register in registry
                return m_pluginRegistry.registerPlugin(metadata)
                    .thenApply(v -> metadata);
            })
            .thenCompose(metadata -> {
                // Load the plugin immediately if enabled
                return loadPlugin(metadata)
                    .thenApply(bundle -> {
                        if (bundle != null) {
                            m_loadedBundles.put(metadata.getPluginId(), bundle);
                            System.out.println("Plugin loaded: " + metadata.getPluginId().getAsString());
                        }
                        return metadata;
                    });
            });
    }
    
    /**
     * Uninstall a plugin.
     */
    public CompletableFuture<Void> uninstallPlugin(NoteBytes pluginId, AsyncNoteBytesWriter progressWriter) {
        PluginMetaData metadata = m_pluginRegistry.getPlugin(pluginId);
        if (metadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Plugin not found: " + pluginId.getAsString())
            );
        }
        
        return unloadPlugin(pluginId)
            .thenAccept(v -> m_pluginRegistry.unregisterPlugin(pluginId))
            .thenCompose(v -> m_pluginDownloader.deletePluginJar(metadata.geNotePath(), progressWriter))
            .thenRun(() -> System.out.println("Plugin uninstalled: " + pluginId.getAsString()));
    }
    
    /**
     * Enable a plugin (and load it if not already loaded).
     */
    public CompletableFuture<Void> enablePlugin(NoteBytes pluginId) {
        return m_pluginRegistry.setPluginEnabled(pluginId, true)
            .thenCompose(v -> {
                PluginMetaData metadata = m_pluginRegistry.getPlugin(pluginId);
                if (metadata != null && !m_loadedBundles.containsKey(pluginId)) {
                    return loadPlugin(metadata)
                        .thenAccept(bundle -> {
                            if (bundle != null) {
                                m_loadedBundles.put(pluginId, bundle);
                            }
                        });
                }
                return CompletableFuture.completedFuture(null);
            });
    }
    
    /**
     * Disable a plugin (unload but keep installed).
     */
    public CompletableFuture<Void> disablePlugin(NoteBytes pluginId) {
        return unloadPlugin(pluginId)
            .thenCompose(v -> m_pluginRegistry.setPluginEnabled(pluginId, false));
    }
    
    /**
     * Get all installed plugins.
     */
    public List<PluginMetaData> getInstalledPlugins() {
        return m_pluginRegistry.getAllPlugins();
    }
    
    /**
     * Check if a plugin is installed.
     */
    public boolean isPluginInstalled(NoteBytes pluginId) {
        return m_pluginRegistry.isPluginInstalled(pluginId);
    }
    
    /**
     * Check if a plugin is loaded.
     */
    public boolean isPluginLoaded(NoteBytes pluginId) {
        return m_loadedBundles.containsKey(pluginId);
    }
    
    /**
     * Load all enabled plugins at startup.
     */
    private CompletableFuture<Map<NoteBytes, org.osgi.framework.Bundle>> loadEnabledPlugins() {
        List<PluginMetaData> enabledPlugins = m_pluginRegistry.getEnabledPlugins();
        
        if (enabledPlugins.isEmpty()) {
            return CompletableFuture.completedFuture(new ConcurrentHashMap<>());
        }
        
        ConcurrentHashMap<NoteBytes, org.osgi.framework.Bundle> bundles = new ConcurrentHashMap<>();
        
        List<CompletableFuture<Void>> loadFutures = new ArrayList<>();
        for (PluginMetaData metadata : enabledPlugins) {
            CompletableFuture<Void> loadFuture = loadPlugin(metadata)
                .thenAccept(bundle -> {
                    if (bundle != null) {
                        bundles.put(metadata.getPluginId(), bundle);
                    }
                })
                .exceptionally(error -> {
                    System.err.println("Failed to load plugin " + 
                        metadata.getPluginId().getAsString() + ": " + error.getMessage());
                    return null;
                });
            loadFutures.add(loadFuture);
        }
        
        return CompletableFuture.allOf(loadFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> bundles);
    }
    
    /**
     * Load a single plugin.
     */
    private CompletableFuture<org.osgi.framework.Bundle> loadPlugin(PluginMetaData metadata) {
        return m_appData.getNoteFile(metadata.geNotePath())
            .thenCompose(noteFile -> {
                // Use OSGiPluginFactory to load from NoteFile
                // This will need to be implemented in OSGiPluginFactory
                return loadPluginFromNoteFile(metadata.getPluginId(), noteFile);
            });
    }
    
    /**
     * Load plugin bundle from NoteFile (placeholder - to be implemented in OSGiPluginFactory).
     */
    private CompletableFuture<org.osgi.framework.Bundle> loadPluginFromNoteFile(
        NoteBytes pluginId, 
        io.netnotes.engine.noteFiles.NoteFile noteFile
    ) {
        // TODO: Implement in OSGiPluginFactory
        // For now, return a completed future
        System.out.println("Loading plugin bundle from NoteFile: " + pluginId.getAsString());
        
        // This should:
        // 1. Read JAR from NoteFile
        // 2. Install bundle in OSGi framework
        // 3. Start bundle
        // 4. Return bundle reference
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Unload a plugin bundle.
     */
    private CompletableFuture<Void> unloadPlugin(NoteBytes pluginId) {
        org.osgi.framework.Bundle bundle = m_loadedBundles.remove(pluginId);
        if (bundle != null) {
            return CompletableFuture.runAsync(() -> {
                try {
                    bundle.stop();
                    bundle.uninstall();
                    System.out.println("Unloaded plugin: " + pluginId.getAsString());
                } catch (Exception e) {
                    System.err.println("Error unloading plugin: " + e.getMessage());
                    throw new RuntimeException("Failed to unload plugin", e);
                }
            }, m_appData.getExecService());
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Shutdown - unload all plugins and close resources.
     */
    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Close all open app boxes
        for (Map.Entry<NoteBytes, AppBox> entry : m_openAppBoxes.entrySet()) {
            futures.add(entry.getValue().shutdown());
        }
        
        // Unload all plugins
        for (NoteBytes pluginId : new ArrayList<>(m_loadedBundles.keySet())) {
            futures.add(unloadPlugin(pluginId));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                try {
                    m_osgiFactory.shutdown();
                } catch (Exception e) {
                    System.err.println("Error shutting down OSGi: " + e.getMessage());
                }
            });
    }
}