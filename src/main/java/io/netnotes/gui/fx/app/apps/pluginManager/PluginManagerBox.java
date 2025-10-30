package io.netnotes.gui.fx.app.apps.pluginManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.plugins.OSGiPluginInformation;
import io.netnotes.engine.plugins.OSGiPluginMetaData;
import io.netnotes.engine.plugins.OSGiPluginRelease;
import io.netnotes.engine.plugins.OSGiPluginReleaseFetcher;
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
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Plugin Manager UI with tabs for Browse and Installed plugins.
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
    
    // Search/filter components
    private javafx.scene.control.TextField m_searchField;
    private javafx.scene.control.ComboBox<String> m_categoryFilter;
    
    // Browse tab components
    private ScrollPane m_browseScrollPane;
    private VBox m_browseListBox;
    private ScrollPaneHelper m_browseScrollHelper;
    
    // Installed tab components
    private ScrollPane m_installedScrollPane;
    private VBox m_installedListBox;
    private ScrollPaneHelper m_installedScrollHelper;
    
    private OSGiPluginReleaseFetcher m_releasesFetcher;
    
    // Reference to plugin manager for operations
    private PluginManager m_pluginManager;
    
    public PluginManagerBox(Stage stage, GitHubInfo gitHubInfo) {
        super(ID, "Plugin Manager");
        m_stage = stage;
        m_gitHubInfo = gitHubInfo;

        m_contentWidth = new SimpleDoubleProperty(FxResourceFactory.STAGE_WIDTH);
        m_contentHeight = new SimpleDoubleProperty(FxResourceFactory.STAGE_HEIGHT);

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
        loadAvailablePlugins();
        
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
        
        // Search field
        m_searchField = new javafx.scene.control.TextField();
        m_searchField.setPromptText("Search plugins...");
        m_searchField.setPrefWidth(200);
        m_searchField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #ffffff; " +
                               "-fx-prompt-text-fill: #888888; -fx-padding: 8px; -fx-background-radius: 5px;");
        m_searchField.textProperty().addListener((_, _, _) -> applyFilters());
        
        // Category filter
        m_categoryFilter = new javafx.scene.control.ComboBox<>();
        m_categoryFilter.getItems().addAll("All Categories", "General", "Utilities", "Development", "Graphics");
        m_categoryFilter.setValue("All Categories");
        m_categoryFilter.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #ffffff;");
        m_categoryFilter.setOnAction(_ -> applyFilters());
        
        m_refreshButton = new Button("↻ Refresh");
        m_refreshButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 8px 15px; -fx-background-radius: 5px;");
        m_refreshButton.setOnAction(_ -> {
            if (m_tabPane.getSelectionModel().getSelectedItem().getText().equals("Browse")) {
                loadAvailablePlugins();
            } else {
                refreshInstalledPlugins();
            }
        });
        
        headerBox.getChildren().addAll(header, spacer, m_searchField, m_categoryFilter, m_refreshButton);
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
    
    private void loadAvailablePlugins() {
        if (m_pluginManager == null) {
            m_statusLabel.setText("Plugin manager not initialized");
            return;
        }
        
        m_statusLabel.setText("Loading available plugins...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        m_browseListBox.getChildren().clear();
        m_refreshButton.setDisable(true);
        
        m_pluginManager.loadAvailablePlugins()
            .thenAccept(apps -> {
                Platform.runLater(() -> {
                    displayBrowsePlugins();
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
    
    private void displayBrowsePlugins() {
        m_browseListBox.getChildren().clear();
        
        List<PluginGroup> groups = m_pluginManager.getGroupManager().getBrowseGroups();
        List<PluginGroup> filteredGroups = applyFilterToGroups(groups);
        
        for (PluginGroup group : filteredGroups) {
            VBox appCard = createBrowseAppCard(group);
            m_browseListBox.getChildren().add(appCard);
        }
        
        if (filteredGroups.isEmpty()) {
            Label emptyLabel = new Label("No plugins match your filters");
            emptyLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 16px;");
            m_browseListBox.getChildren().add(emptyLabel);
        }
    }
    
    private void applyFilters() {
        if (m_tabPane.getSelectionModel().getSelectedItem().getText().equals("Browse")) {
            displayBrowsePlugins();
        } else {
            displayInstalledPlugins(
                applyFilterToGroups(m_pluginManager.getGroupManager().getInstalledGroups())
            );
        }
    }
    
    private List<PluginGroup> applyFilterToGroups(List<PluginGroup> groups) {
        String searchText = m_searchField.getText().toLowerCase().trim();
        String category = m_categoryFilter.getValue();
        
        return groups.stream()
            .filter(group -> {
                // Apply search filter
                if (!searchText.isEmpty()) {
                    String name = group.getAppName().toLowerCase();
                    String desc = group.getPluginInfo().getDescription().toLowerCase();
                    if (!name.contains(searchText) && !desc.contains(searchText)) {
                        return false;
                    }
                }
                
                // Apply category filter
                if (!"All Categories".equals(category)) {
                    if (!category.equals(group.getPluginInfo().getCategory())) {
                        return false;
                    }
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }
    
    private VBox createBrowseAppCard(PluginGroup group) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; " +
                     "-fx-border-color: #3c3c3c; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        HBox topBox = new HBox(15);
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        // Placeholder for app icon (will be loaded asynchronously)
        ImageView iconView = new ImageView();
        iconView.setFitWidth(48);
        iconView.setFitHeight(48);
        iconView.setPreserveRatio(true);
        topBox.getChildren().add(iconView);
        
        // Load small icon asynchronously
        group.getSmallIcon(m_pluginManager.getAppData(), TaskUtils.getVirtualExecutor())
            .thenAccept(image -> Platform.runLater(() -> iconView.setImage(image)))
            .exceptionally(e -> {
                System.err.println("Failed to load icon for " + group.getAppName() + ": " + e.getMessage());
                return null;
            });
        
        // App info
        VBox infoBox = new VBox(5);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        OSGiPluginInformation pluginInfo = group.getPluginInfo();
        
        Label nameLabel = new Label(pluginInfo.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label descLabel = new Label(pluginInfo.getDescription());
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        descLabel.setWrapText(true);
        
        infoBox.getChildren().addAll(nameLabel, descLabel);
        
        // Action buttons
        VBox actionBox = new VBox(5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button viewDetailsBtn = new Button("View Details");
        viewDetailsBtn.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        viewDetailsBtn.setOnAction(_ -> showPluginDetails(group));
        
        Button installBtn = new Button("Install");
        installBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                                 "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        
        // Check if already installed
        if (group.hasInstalledVersions()) {
            installBtn.setText("Installed");
            installBtn.setDisable(true);
            installBtn.setStyle("-fx-background-color: #666666; -fx-text-fill: #ffffff; " +
                               "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        }
        
        installBtn.setOnAction(_ -> installPlugin(group, installBtn));
        
        actionBox.getChildren().addAll(viewDetailsBtn, installBtn);
        
        topBox.getChildren().addAll(infoBox, actionBox);
        
        // GitHub source info
        if (pluginInfo.getGitHubJar() != null) {
            GitHubInfo ghInfo = pluginInfo.getGitHubJar().getGitHubInfo();
            Label ghLabel = new Label("Source: " + ghInfo.getUser() + "/" + ghInfo.getProject());
            ghLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
            card.getChildren().addAll(topBox, ghLabel);
        } else {
            card.getChildren().add(topBox);
        }
        
        return card;
    }
    
    private void refreshInstalledPlugins() {
        if (m_pluginManager == null) {
            m_statusLabel.setText("Plugin manager not initialized");
            return;
        }
        
        m_statusLabel.setText("Loading installed plugins...");
        m_installedListBox.getChildren().clear();
        
        CompletableFuture.runAsync(() -> {
            List<PluginGroup> groups = m_pluginManager.getGroupManager().getInstalledGroups();
            Platform.runLater(() -> {
                displayInstalledPlugins(groups);
                m_statusLabel.setText(groups.size() + " plugins installed");
            });
        }, TaskUtils.getVirtualExecutor());
    }
    
    private void displayInstalledPlugins(List<PluginGroup> groups) {
        m_installedListBox.getChildren().clear();
        
        if (groups.isEmpty()) {
            Label emptyLabel = new Label("No plugins installed");
            emptyLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 16px;");
            m_installedListBox.getChildren().add(emptyLabel);
            return;
        }
        
        for (PluginGroup group : groups) {
            VBox pluginCard = createInstalledPluginCard(group);
            m_installedListBox.getChildren().add(pluginCard);
        }
    }
    
    private VBox createInstalledPluginCard(PluginGroup group) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; " +
                     "-fx-border-color: #3c3c3c; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        HBox topBox = new HBox(15);
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        // Plugin icon
        ImageView iconView = new ImageView();
        iconView.setFitWidth(48);
        iconView.setFitHeight(48);
        iconView.setPreserveRatio(true);
        topBox.getChildren().add(iconView);
        
        // Load icon asynchronously
        group.getSmallIcon(m_pluginManager.getAppData(), TaskUtils.getVirtualExecutor())
            .thenAccept(image -> Platform.runLater(() -> iconView.setImage(image)))
            .exceptionally(e -> null);
        
        // Plugin info
        VBox infoBox = new VBox(5);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label nameLabel = new Label(group.getAppName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        // Show installed versions
        List<OSGiPluginMetaData> versions = group.getInstalledVersions();
        OSGiPluginMetaData enabledVersion = group.getEnabledVersion();
        
        StringBuilder versionsText = new StringBuilder("Installed versions: ");
        for (int i = 0; i < versions.size(); i++) {
            OSGiPluginMetaData v = versions.get(i);
            if (i > 0) versionsText.append(", ");
            versionsText.append(v.getRelease().getTagName());
            if (v.equals(enabledVersion)) {
                versionsText.append(" (enabled)");
            }
        }
        
        Label versionLabel = new Label(versionsText.toString());
        versionLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        versionLabel.setWrapText(true);
        
        Label statusLabel = new Label("Status: " + (enabledVersion != null ? "Enabled" : "Disabled"));
        statusLabel.setStyle("-fx-text-fill: " + 
            (enabledVersion != null ? "#5cb85c" : "#888888") + "; -fx-font-size: 12px;");
        
        infoBox.getChildren().addAll(nameLabel, versionLabel, statusLabel);
        
        // Action buttons
        VBox actionBox = new VBox(5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button manageBtn = new Button("Manage Versions");
        manageBtn.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: #ffffff; " +
                          "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        manageBtn.setOnAction(_ -> showVersionManager(group));
        
        Button uninstallBtn = new Button("Uninstall All");
        uninstallBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: #ffffff; " +
                             "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        uninstallBtn.setOnAction(_ -> uninstallAllVersions(group));
        
        actionBox.getChildren().addAll(manageBtn, uninstallBtn);
        
        topBox.getChildren().addAll(infoBox, actionBox);
        card.getChildren().add(topBox);
        
        return card;
    }
    
    private void showPluginDetails(PluginGroup group) {
        m_statusLabel.setText("Loading details for " + group.getAppName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(group.getAppName());
        dialog.setHeaderText("Plugin Details");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #2b2b2b;");
        content.setPrefWidth(600);
        
        OSGiPluginInformation pluginInfo = group.getPluginInfo();
        
        // App header with icon
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        ImageView iconView = new ImageView();
        iconView.setFitWidth(64);
        iconView.setFitHeight(64);
        headerBox.getChildren().add(iconView);
        
        // Load full icon
        group.getFullIcon(m_pluginManager.getAppData(), TaskUtils.getVirtualExecutor())
            .thenAccept(image -> Platform.runLater(() -> iconView.setImage(image)))
            .exceptionally(e -> null);
        
        VBox titleBox = new VBox(5);
        Label nameLabel = new Label(pluginInfo.getName());
        nameLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label descLabel = new Label(pluginInfo.getDescription());
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
        m_releasesFetcher.fetchReleasesForApp(true, pluginInfo)
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
                            HBox releaseItem = createReleaseItem(release, group);
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
    
    private HBox createReleaseItem(OSGiPluginRelease release, PluginGroup group) {
        HBox box = new HBox(15);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5px;");
        box.setAlignment(Pos.CENTER_LEFT);
        
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label versionLabel = new Label("Version: " + release.getTagName());
        versionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label sizeLabel = new Label("Size: " + formatSize(release.getSize()));
        sizeLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        
        infoBox.getChildren().addAll(versionLabel, sizeLabel);
        
        // Check if this version is installed
        String releasePluginId = release.getPluginId();
        boolean isInstalled = m_pluginManager.isPluginInstalled(releasePluginId);
        
        Button installBtn = new Button(isInstalled ? "Installed" : "Install");
        installBtn.setStyle("-fx-background-color: " + (isInstalled ? "#666666" : "#5cb85c") + 
                            "; -fx-text-fill: #ffffff; -fx-padding: 5px 15px; -fx-background-radius: 5px;");
        installBtn.setDisable(isInstalled);
        installBtn.setOnAction(_ -> installRelease(release, installBtn));
        
        box.getChildren().addAll(infoBox, installBtn);
        return box;
    }
    
    private void showVersionManager(PluginGroup group) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Manage " + group.getAppName());
        dialog.setHeaderText("Installed Versions");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #2b2b2b;");
        content.setPrefWidth(500);
        
        List<OSGiPluginMetaData> versions = group.getInstalledVersions();
        OSGiPluginMetaData enabledVersion = group.getEnabledVersion();
        
        for (OSGiPluginMetaData metadata : versions) {
            HBox versionBox = new HBox(15);
            versionBox.setPadding(new Insets(10));
            versionBox.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5px;");
            versionBox.setAlignment(Pos.CENTER_LEFT);
            
            VBox infoBox = new VBox(3);
            HBox.setHgrow(infoBox, Priority.ALWAYS);
            
            Label versionLabel = new Label("Version: " + metadata.getRelease().getTagName());
            versionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
            
            Label statusLabel = new Label(metadata.equals(enabledVersion) ? "● Enabled" : "○ Disabled");
            statusLabel.setStyle("-fx-text-fill: " + 
                (metadata.equals(enabledVersion) ? "#5cb85c" : "#888888") + "; -fx-font-size: 12px;");
            
            infoBox.getChildren().addAll(versionLabel, statusLabel);
            
            HBox buttonBox = new HBox(5);
            
            if (!metadata.equals(enabledVersion)) {
                Button enableBtn = new Button("Enable");
                enableBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                                  "-fx-padding: 5px 10px; -fx-background-radius: 5px;");
                enableBtn.setOnAction(_ -> {
                    enableVersion(metadata);
                    dialog.close();
                    refreshInstalledPlugins();
                });
                buttonBox.getChildren().add(enableBtn);
            } else {
                Button disableBtn = new Button("Disable");
                disableBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: #ffffff; " +
                                   "-fx-padding: 5px 10px; -fx-background-radius: 5px;");
                disableBtn.setOnAction(_ -> {
                    disableVersion(metadata);
                    dialog.close();
                    refreshInstalledPlugins();
                });
                buttonBox.getChildren().add(disableBtn);
            }
            
            Button uninstallBtn = new Button("Uninstall");
            uninstallBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: #ffffff; " +
                                 "-fx-padding: 5px 10px; -fx-background-radius: 5px;");
            uninstallBtn.setOnAction(_ -> {
                uninstallVersion(metadata);
                dialog.close();
                refreshInstalledPlugins();
            });
            buttonBox.getChildren().add(uninstallBtn);
            
            versionBox.getChildren().addAll(infoBox, buttonBox);
            content.getChildren().add(versionBox);
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
        dialog.show();
    }
    
    private void installPlugin(PluginGroup group, Button installBtn) {
        m_statusLabel.setText("Fetching latest release for " + group.getAppName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        installBtn.setDisable(true);
        
        m_releasesFetcher.fetchReleasesForApp(false, group.getPluginInfo())
            .thenAccept(releases -> {
                Platform.runLater(() -> {
                    if (!releases.isEmpty()) {
                        OSGiPluginRelease latestRelease = releases.get(0);
                        installRelease(latestRelease, installBtn);
                    } else {
                        m_statusLabel.setText("No releases found for " + group.getAppName());
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
                             " version " + release.getTagName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        installBtn.setDisable(true);
        
        m_pluginManager.installPlugin(release, true, null)
            .thenAccept(metadata -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Successfully installed " + 
                        metadata.getName() + " version " + metadata.getRelease().getTagName());
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                    
                    installBtn.setText("Installed");
                    installBtn.setStyle("-fx-background-color: #666666; -fx-text-fill: #ffffff; " +
                                       "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
                    
                    // Refresh both tabs
                    loadAvailablePlugins();
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
    
    private void enableVersion(OSGiPluginMetaData metadata) {
        if (m_pluginManager == null) return;
        
        m_statusLabel.setText("Enabling " + metadata.getName() + " version " + 
            metadata.getRelease().getTagName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        m_pluginManager.enablePlugin(metadata.getPluginId())
            .thenRun(() -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Plugin enabled successfully");
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Error: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
    }
    
    private void disableVersion(OSGiPluginMetaData metadata) {
        if (m_pluginManager == null) return;
        
        m_statusLabel.setText("Disabling " + metadata.getName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        m_pluginManager.disablePlugin(metadata.getPluginId())
            .thenRun(() -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Plugin disabled successfully");
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Error: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
    }
    
    private void uninstallVersion(OSGiPluginMetaData metadata) {
        if (m_pluginManager == null) return;
        
        m_statusLabel.setText("Uninstalling " + metadata.getName() + " version " + 
            metadata.getRelease().getTagName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        m_pluginManager.uninstallPlugin(metadata.getPluginId())
            .thenRun(() -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Plugin uninstalled successfully");
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
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
    
    private void uninstallAllVersions(PluginGroup group) {
        if (m_pluginManager == null) return;
        
        List<OSGiPluginMetaData> versions = group.getInstalledVersions();
        if (versions.isEmpty()) return;
        
        m_statusLabel.setText("Uninstalling all versions of " + group.getAppName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        CompletableFuture<?>[] futures = versions.stream()
            .map(metadata -> m_pluginManager.uninstallPlugin(metadata.getPluginId()))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures)
            .thenRun(() -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("All versions uninstalled successfully");
                    m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
                    refreshInstalledPlugins();
                    loadAvailablePlugins();
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
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}