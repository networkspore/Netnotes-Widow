package io.netnotes.gui.fx.app.apps.pluginManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.plugins.OSGiPluginInformation;
import io.netnotes.engine.plugins.OSGiPluginRelease;
import io.netnotes.engine.plugins.OSGiPluginReleaseFetcher;
import io.netnotes.engine.plugins.OSGiUpdateLoader;
import io.netnotes.engine.plugins.PluginMetaData;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.contentManager.AppBox;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.application.Platform;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Enhanced Plugin Manager UI with tabs for Browse and Installed plugins.
 */
class PluginManagerBox extends AppBox {

    public static final NoteBytesReadOnly ID = new NoteBytesReadOnly("PluginManager-Main");
    
    private final GitHubInfo m_gitHubInfo;
    private final Stage m_stage;
    private final DoubleProperty m_contentWidth;
    private final DoubleProperty m_contentHeight;
    
    private VBox m_mainContainer;
    private TabPane m_tabPane;
    private Label m_statusLabel;
    private Button m_refreshButton;
    private HBox m_headerBox;
    
    // Browse tab components
    private ScrollPane m_browseScrollPane;
    private VBox m_browseListBox;
    private ScrollPaneHelper m_browseScrollHelper;
    
    // Installed tab components
    private ScrollPane m_installedScrollPane;
    private VBox m_installedListBox;
    private ScrollPaneHelper m_installedScrollHelper;
    
    private List<OSGiPluginInformation> m_availableApps;
    private OSGiUpdateLoader m_appsLoader;
    private OSGiPluginReleaseFetcher m_releasesFetcher;
    
    // Reference to plugin manager for operations
    private PluginManager m_pluginManager;
    
    public PluginManagerBox(Stage stage, GitHubInfo gitHubInfo) {
        super(new NoteBytes("PluginManagerBox"), "Plugin Manager");
        m_stage = stage;
        m_gitHubInfo = gitHubInfo;

        m_contentWidth = new SimpleDoubleProperty(FxResourceFactory.STAGE_WIDTH);
        m_contentHeight = new SimpleDoubleProperty(FxResourceFactory.STAGE_HEIGHT);

        m_appsLoader = new OSGiUpdateLoader(gitHubInfo, TaskUtils.getVirtualExecutor());
        m_releasesFetcher = new OSGiPluginReleaseFetcher(TaskUtils.getVirtualExecutor());
    }
    
    /**
     * Set the plugin manager instance for operations.
     */
    public void setPluginManager(PluginManager pluginManager) {
        m_pluginManager = pluginManager;
    }
    
    @Override
    protected void initialize() {
        m_mainContainer = new VBox(15);
        m_mainContainer.setPadding(new Insets(20));
        
        // Header
        m_headerBox = createHeader();
        
        // Tab pane
        m_tabPane = new TabPane();
        m_tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(m_tabPane, Priority.ALWAYS);
        
        // Browse tab
        Tab browseTab = new Tab("Browse");
        browseTab.setContent(createBrowseTab());
        
        // Installed tab
        Tab installedTab = new Tab("Installed");
        installedTab.setContent(createInstalledTab());
        
        m_tabPane.getTabs().addAll(browseTab, installedTab);
        
        // Status label
        m_statusLabel = new Label("Ready");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        m_mainContainer.getChildren().addAll(m_headerBox, m_tabPane, m_statusLabel);
        this.setCenter(m_mainContainer);
        
        // Register with DeferredLayoutManager
        DeferredLayoutManager.register(m_stage, m_mainContainer, _ -> {
            return new LayoutData.Builder()
                .width(m_contentWidth.get())
                .height(m_contentHeight.get())
                .build();
        });
        
        // Listen for content dimension changes
        m_contentWidth.addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(m_mainContainer);
            if (m_browseScrollHelper != null) m_browseScrollHelper.refresh();
            if (m_installedScrollHelper != null) m_installedScrollHelper.refresh();
        });
        
        m_contentHeight.addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(m_mainContainer);
            if (m_browseScrollHelper != null) m_browseScrollHelper.refresh();
            if (m_installedScrollHelper != null) m_installedScrollHelper.refresh();
        });
        
        // Load available apps
        loadAvailableApps();
        
        // Load installed plugins when tab is selected
        m_tabPane.getSelectionModel().selectedItemProperty().addListener((_, _, newTab) -> {
            if (newTab.getText().equals("Installed")) {
                refreshInstalledPlugins();
            }
        });
    }
    
    private HBox createHeader() {
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label header = new Label("Plugin Manager");
        header.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        m_refreshButton = new Button("↻ Refresh");
        m_refreshButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 8px 15px; -fx-background-radius: 5px;");
        m_refreshButton.setOnAction(_ -> {
            if (m_tabPane.getSelectionModel().getSelectedItem().getText().equals("Browse")) {
                loadAvailableApps();
            } else {
                refreshInstalledPlugins();
            }
        });
        
        headerBox.getChildren().addAll(header, spacer, m_refreshButton);
        return headerBox;
    }
    
    private VBox createBrowseTab() {
        VBox container = new VBox(10);
        
        m_browseListBox = new VBox(10);
        m_browseListBox.setPadding(new Insets(10));
        
        m_browseScrollPane = new ScrollPane(m_browseListBox);
        m_browseScrollPane.setFitToWidth(true);
        m_browseScrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        VBox.setVgrow(m_browseScrollPane, Priority.ALWAYS);
        
        DoubleExpression[] heightOffsets = {
            m_headerBox.heightProperty(),
            new SimpleDoubleProperty(120)
        };
        
        m_browseScrollHelper = new ScrollPaneHelper(
            m_stage, m_browseScrollPane, m_browseListBox, 
            m_contentWidth, m_contentHeight, null, heightOffsets
        );
        
        container.getChildren().add(m_browseScrollPane);
        return container;
    }
    
    private VBox createInstalledTab() {
        VBox container = new VBox(10);
        
        m_installedListBox = new VBox(10);
        m_installedListBox.setPadding(new Insets(10));
        
        m_installedScrollPane = new ScrollPane(m_installedListBox);
        m_installedScrollPane.setFitToWidth(true);
        m_installedScrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        VBox.setVgrow(m_installedScrollPane, Priority.ALWAYS);
        
        DoubleExpression[] heightOffsets = {
            m_headerBox.heightProperty(),
            new SimpleDoubleProperty(120)
        };
        
        m_installedScrollHelper = new ScrollPaneHelper(
            m_stage, m_installedScrollPane, m_installedListBox,
            m_contentWidth, m_contentHeight, null, heightOffsets
        );
        
        container.getChildren().add(m_installedScrollPane);
        return container;
    }
    
    private void loadAvailableApps() {
        m_statusLabel.setText("Loading available plugins...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        m_browseListBox.getChildren().clear();
        m_refreshButton.setDisable(true);
        
        m_appsLoader.loadAvailableApps()
            .thenAccept(apps -> {
                Platform.runLater(() -> {
                    m_availableApps = apps;
                    displayBrowseApps(apps);
                    m_statusLabel.setText("Found " + apps.size() + " available plugins");
                    m_refreshButton.setDisable(false);
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Error loading plugins: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                    m_refreshButton.setDisable(false);
                });
                return null;
            });
    }
    
    private void displayBrowseApps(List<OSGiPluginInformation> apps) {
        m_browseListBox.getChildren().clear();
        
        for (OSGiPluginInformation app : apps) {
            VBox appCard = createBrowseAppCard(app);
            m_browseListBox.getChildren().add(appCard);
        }
    }
    
    private VBox createBrowseAppCard(OSGiPluginInformation app) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; " +
                     "-fx-border-color: #3c3c3c; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        HBox topBox = new HBox(15);
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        // App icon
        if (app.getIcon() != null) {
            javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(app.getIcon());
            iconView.setFitWidth(48);
            iconView.setFitHeight(48);
            iconView.setPreserveRatio(true);
            topBox.getChildren().add(iconView);
        }
        
        // App info
        VBox infoBox = new VBox(5);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label nameLabel = new Label(app.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label descLabel = new Label(app.getDescription());
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        descLabel.setWrapText(true);
        
        infoBox.getChildren().addAll(nameLabel, descLabel);
        
        // Action buttons
        VBox actionBox = new VBox(5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button viewDetailsBtn = new Button("View Details");
        viewDetailsBtn.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        viewDetailsBtn.setOnAction(_ -> showAppDetails(app));
        
        Button installBtn = new Button("Install");
        installBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                                 "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        
        // Check if already installed
        if (m_pluginManager != null) {
            String pluginId = sanitizePluginId(app.getName());
            if (m_pluginManager.isPluginInstalled(new NoteBytes(pluginId))) {
                installBtn.setText("Installed");
                installBtn.setDisable(true);
                installBtn.setStyle("-fx-background-color: #666666; -fx-text-fill: #ffffff; " +
                                   "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
            }
        }
        
        installBtn.setOnAction(_ -> installApp(app, installBtn));
        
        actionBox.getChildren().addAll(viewDetailsBtn, installBtn);
        
        topBox.getChildren().addAll(infoBox, actionBox);
        
        // GitHub source info
        if (app.getGitHubFiles() != null && app.getGitHubFiles().length > 0) {
            Label ghLabel = new Label("Source: " + 
                app.getGitHubFiles()[0].getGitHubInfo().getUser() + "/" +
                app.getGitHubFiles()[0].getGitHubInfo().getProject());
            ghLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
            card.getChildren().addAll(topBox, ghLabel);
        } else {
            card.getChildren().add(topBox);
        }
        
        return card;
    }
    
    private void refreshInstalledPlugins() {
        m_statusLabel.setText("Loading installed plugins...");
        m_installedListBox.getChildren().clear();
        
        if (m_pluginManager == null) {
            m_statusLabel.setText("Plugin manager not initialized");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            List<PluginMetaData> installed = m_pluginManager.getInstalledPlugins();
            Platform.runLater(() -> {
                displayInstalledPlugins(installed);
                m_statusLabel.setText(installed.size() + " plugins installed");
            });
        }, TaskUtils.getVirtualExecutor());
    }
    
    private void displayInstalledPlugins(List<PluginMetaData> plugins) {
        m_installedListBox.getChildren().clear();
        
        if (plugins.isEmpty()) {
            Label emptyLabel = new Label("No plugins installed");
            emptyLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 16px;");
            m_installedListBox.getChildren().add(emptyLabel);
            return;
        }
        
        for (PluginMetaData plugin : plugins) {
            VBox pluginCard = createInstalledPluginCard(plugin);
            m_installedListBox.getChildren().add(pluginCard);
        }
    }
    
    private VBox createInstalledPluginCard(PluginMetaData plugin) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; " +
                     "-fx-border-color: #3c3c3c; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        HBox topBox = new HBox(15);
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        // Plugin info
        VBox infoBox = new VBox(5);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label nameLabel = new Label(plugin.getPluginId().getAsString());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label versionLabel = new Label("Version: " + plugin.getVersion());
        versionLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        
        Label statusLabel = new Label("Status: " + (plugin.isEnabled() ? "Enabled" : "Disabled"));
        statusLabel.setStyle("-fx-text-fill: " + 
            (plugin.isEnabled() ? "#5cb85c" : "#888888") + "; -fx-font-size: 12px;");
        
        boolean isLoaded = m_pluginManager != null && 
                          m_pluginManager.isPluginLoaded(plugin.getPluginId());
        Label loadedLabel = new Label(isLoaded ? "● Loaded" : "○ Not Loaded");
        loadedLabel.setStyle("-fx-text-fill: " + 
            (isLoaded ? "#5cb85c" : "#888888") + "; -fx-font-size: 11px;");
        
        infoBox.getChildren().addAll(nameLabel, versionLabel, statusLabel, loadedLabel);
        
        // Action buttons
        VBox actionBox = new VBox(5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button toggleBtn = new Button(plugin.isEnabled() ? "Disable" : "Enable");
        toggleBtn.setStyle("-fx-background-color: " + 
            (plugin.isEnabled() ? "#d9534f" : "#5cb85c") +
            "; -fx-text-fill: #ffffff; -fx-padding: 6px 12px; -fx-background-radius: 5px;");
        toggleBtn.setOnAction(_ -> togglePluginEnabled(plugin, toggleBtn));
        
        Button uninstallBtn = new Button("Uninstall");
        uninstallBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: #ffffff; " +
                             "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        uninstallBtn.setOnAction(_ -> uninstallPlugin(plugin));
        
        actionBox.getChildren().addAll(toggleBtn, uninstallBtn);
        
        topBox.getChildren().addAll(infoBox, actionBox);
        
        // Path info
        Label pathLabel = new Label("Path: " + plugin.geNotePath().toString());
        pathLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        
        card.getChildren().addAll(topBox, pathLabel);
        return card;
    }
    
    private void showAppDetails(OSGiPluginInformation app) {
        m_statusLabel.setText("Loading details for " + app.getName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(app.getName());
        dialog.setHeaderText("Plugin Details");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #2b2b2b;");
        content.setPrefWidth(600);
        
        // App header
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        if (app.getIcon() != null) {
            javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(app.getIcon());
            iconView.setFitWidth(64);
            iconView.setFitHeight(64);
            headerBox.getChildren().add(iconView);
        }
        
        VBox titleBox = new VBox(5);
        Label nameLabel = new Label(app.getName());
        nameLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label descLabel = new Label(app.getDescription());
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");
        descLabel.setWrapText(true);
        
        titleBox.getChildren().addAll(nameLabel, descLabel);
        headerBox.getChildren().add(titleBox);
        content.getChildren().add(headerBox);
        
        // Releases section
        Label releasesLabel = new Label("Available Releases");
        releasesLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        content.getChildren().add(releasesLabel);
        
        Label loadingLabel = new Label("Loading releases...");
        loadingLabel.setStyle("-fx-text-fill: #888888;");
        content.getChildren().add(loadingLabel);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
        
        // Load releases
        m_releasesFetcher.fetchReleasesForApp(app)
            .thenAccept(releases -> {
                Platform.runLater(() -> {
                    content.getChildren().remove(loadingLabel);
                    
                    if (releases.isEmpty()) {
                        Label noReleasesLabel = new Label("No releases found");
                        noReleasesLabel.setStyle("-fx-text-fill: #888888;");
                        content.getChildren().add(noReleasesLabel);
                    } else {
                        VBox releasesBox = new VBox(10);
                        for (OSGiPluginRelease release : releases) {
                            HBox releaseItem = createReleaseItem(release);
                            releasesBox.getChildren().add(releaseItem);
                        }
                        content.getChildren().add(releasesBox);
                    }
                    
                    m_statusLabel.setText("Ready");
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    content.getChildren().remove(loadingLabel);
                    Label errorLabel = new Label("Error: " + error.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #ff6666;");
                    content.getChildren().add(errorLabel);
                    
                    m_statusLabel.setText("Error loading releases");
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
        
        dialog.show();
    }
    
    private HBox createReleaseItem(OSGiPluginRelease release) {
        HBox box = new HBox(15);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5px;");
        box.setAlignment(Pos.CENTER_LEFT);
        
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label versionLabel = new Label("Version: " + release.getVersion());
        versionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label tagLabel = new Label("Tag: " + release.getTagName());
        tagLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        
        Label sizeLabel = new Label("Size: " + formatSize(release.getSize()));
        sizeLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        
        infoBox.getChildren().addAll(versionLabel, tagLabel, sizeLabel);
        
        Button installBtn = new Button("Install");
        installBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                            "-fx-padding: 5px 15px; -fx-background-radius: 5px;");
        installBtn.setOnAction(_ -> installRelease(release, installBtn));
        
        box.getChildren().addAll(infoBox, installBtn);
        return box;
    }
    
    private void installApp(OSGiPluginInformation app, Button installBtn) {
        m_statusLabel.setText("Fetching latest release for " + app.getName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        installBtn.setDisable(true);
        
        m_releasesFetcher.fetchReleasesForApp(app)
            .thenAccept(releases -> {
                Platform.runLater(() -> {
                    if (!releases.isEmpty()) {
                        OSGiPluginRelease latestRelease = releases.get(0);
                        installRelease(latestRelease, installBtn);
                    } else {
                        m_statusLabel.setText("No releases found for " + app.getName());
                        m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                        installBtn.setDisable(false);
                    }
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Error: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                    installBtn.setDisable(false);
                });
                return null;
            });
    }
    
    private void installRelease(OSGiPluginRelease release, Button installBtn) {
        if (m_pluginManager == null) {
            m_statusLabel.setText("Plugin manager not initialized");
            m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
            return;
        }
        
        m_statusLabel.setText("Installing " + release.getPluginInfo().getName() + 
                             " version " + release.getVersion() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        installBtn.setDisable(true);
        
        m_pluginManager.installPlugin(release, null)
            .thenAccept(metadata -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Successfully installed " + 
                        metadata.getPluginId().getAsString() + " version " + metadata.getVersion());
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                    
                    installBtn.setText("Installed");
                    installBtn.setStyle("-fx-background-color: #666666; -fx-text-fill: #ffffff; " +
                                       "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Installation failed: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                    installBtn.setDisable(false);
                });
                return null;
            });
    }
    
    private void togglePluginEnabled(PluginMetaData plugin, Button toggleBtn) {
        if (m_pluginManager == null) {
            return;
        }
        
        boolean currentlyEnabled = plugin.isEnabled();
        toggleBtn.setDisable(true);
        
        CompletableFuture<Void> future = currentlyEnabled ?
            m_pluginManager.disablePlugin(plugin.getPluginId()) :
            m_pluginManager.enablePlugin(plugin.getPluginId());
        
        future.thenRun(() -> {
            Platform.runLater(() -> {
                m_statusLabel.setText("Plugin " + plugin.getPluginId().getAsString() + 
                    (currentlyEnabled ? " disabled" : " enabled"));
                m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                refreshInstalledPlugins();
            });
        })
        .exceptionally(error -> {
            Platform.runLater(() -> {
                m_statusLabel.setText("Error: " + error.getMessage());
                m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                toggleBtn.setDisable(false);
            });
            return null;
        });
    }
    
    private void uninstallPlugin(PluginMetaData plugin) {
        if (m_pluginManager == null) {
            return;
        }
        
        m_statusLabel.setText("Uninstalling " + plugin.getPluginId().getAsString() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        m_pluginManager.uninstallPlugin(plugin.getPluginId(), null)
            .thenRun(() -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Successfully uninstalled " + 
                        plugin.getPluginId().getAsString());
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                    refreshInstalledPlugins();
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Uninstall failed: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private String sanitizePluginId(String pluginName) {
        return pluginName
            .toLowerCase()
            .replaceAll("[^a-z0-9-_]", "-")
            .replaceAll("-+", "-");
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}