package io.netnotes.gui.fx.app.apps.appManager;


import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.gui.fx.app.apps.AppInformation;
import io.netnotes.gui.fx.app.apps.AppRelease;
import io.netnotes.gui.fx.app.apps.AppReleasesFetcher;
import io.netnotes.gui.fx.app.apps.AvailableAppsLoader;
import io.netnotes.gui.fx.components.stages.tabManager.AppBox;
import io.netnotes.gui.fx.display.FxResourceFactory;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main AppManager view - shows available apps for installation.
 */
class AppManagerBox extends AppBox {

    public static final NoteBytesReadOnly ID = new NoteBytesReadOnly("AppManager-Main");
    
    private final GitHubInfo m_gitHubInfo;
    private final javafx.stage.Stage m_stage;
    private final DoubleProperty m_contentWidth;
    private final DoubleProperty m_contentHeight;

    
    private VBox m_mainContainer;
    private ScrollPane m_appsScrollPane;
    private VBox m_appsListBox;
    private Label m_statusLabel;
    private Button m_refreshButton;
    private HBox m_headerBox;
    
    private ScrollPaneHelper m_scrollHelper;
    
    private List<AppInformation> m_availableApps;
    private AvailableAppsLoader m_appsLoader;
    private AppReleasesFetcher m_releasesFetcher;
    
    public AppManagerBox(Stage stage,
                        GitHubInfo gitHubInfo) {
        super(new NoteBytes("AppManagerBox"), "App Manager");
        m_stage = stage;
        m_gitHubInfo = gitHubInfo;

        m_contentWidth = new SimpleDoubleProperty(FxResourceFactory.STAGE_WIDTH);
        m_contentHeight = new SimpleDoubleProperty(FxResourceFactory.STAGE_HEIGHT);

        m_appsLoader = new AvailableAppsLoader(gitHubInfo, TaskUtils.getVirtualExecutor());
        m_releasesFetcher = new AppReleasesFetcher(TaskUtils.getVirtualExecutor());
    }
    
    @Override
    protected void initialize() {
        m_mainContainer = new VBox(15);
        m_mainContainer.setPadding(new Insets(20));
        
        // Header
        m_headerBox = createHeader();
        
        // Apps list content
        m_appsListBox = new VBox(10);
        m_appsListBox.setPadding(new Insets(10));
        
        // ScrollPane for apps
        m_appsScrollPane = new ScrollPane(m_appsListBox);
        m_appsScrollPane.setFitToWidth(true);
        m_appsScrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        VBox.setVgrow(m_appsScrollPane, Priority.ALWAYS);
        
        // Status label
        m_statusLabel = new Label("Loading available apps...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        // Setup ScrollPaneHelper with DeferredLayoutManager
        DoubleExpression[] heightOffsets = {
            m_headerBox.heightProperty(),
            new SimpleDoubleProperty(80)
        };
        
        m_scrollHelper = new ScrollPaneHelper(
            m_stage,
            m_appsScrollPane, 
            m_appsListBox, 
            m_contentWidth,
            m_contentHeight,
            null,
            heightOffsets
        );
        
        m_mainContainer.getChildren().addAll(m_headerBox, m_appsScrollPane, m_statusLabel);
        this.setCenter(m_mainContainer);
        
        // Register main container with DeferredLayoutManager
        DeferredLayoutManager.register(
            m_stage, m_mainContainer, ctx -> {
                return new LayoutData.Builder()
                    .width(m_contentWidth.get())
                    .height(m_contentHeight.get())
                    .build();
            }
        );
        
        // Listen for content dimension changes
        m_contentWidth.addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(m_mainContainer);
            m_scrollHelper.refresh();
        });
        
        m_contentHeight.addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(m_mainContainer);
            m_scrollHelper.refresh();
        });
        
        // Load apps
        loadAvailableApps();
    }
    
    private HBox createHeader() {
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
       Label header = new Label("Application Store");
        header.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        m_refreshButton = new Button("↻ Refresh");
        m_refreshButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 8px 15px; -fx-background-radius: 5px;");
        m_refreshButton.setOnAction(e -> loadAvailableApps());
        
        headerBox.getChildren().addAll(header, spacer, m_refreshButton);
        return headerBox;
    }
    
    private void loadAvailableApps() {
        m_statusLabel.setText("Loading available apps...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        m_appsListBox.getChildren().clear();
        m_refreshButton.setDisable(true);
        
        m_appsLoader.loadAvailableApps()
            .thenAccept(apps -> {
                Platform.runLater(() -> {
                    m_availableApps = apps;
                    displayApps(apps);
                    m_statusLabel.setText("Found " + apps.size() + " available applications");
                    m_refreshButton.setDisable(false);
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    m_statusLabel.setText("Error loading apps: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                    m_refreshButton.setDisable(false);
                });
                return null;
            });
    }
    
    private void displayApps(List<AppInformation> apps) {
        m_appsListBox.getChildren().clear();
        
        for (AppInformation app : apps) {
            VBox appCard = createAppCard(app);
            m_appsListBox.getChildren().add(appCard);
        }
    }
    
    private VBox createAppCard(AppInformation app) {
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
        viewDetailsBtn.setOnAction(e -> showAppDetails(app));
        
        Button installBtn = new Button("Install");
        installBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                                 "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        installBtn.setOnAction(e -> installApp(app));
        
        actionBox.getChildren().addAll(viewDetailsBtn, installBtn);
        
        topBox.getChildren().addAll(infoBox, actionBox);
        
        // GitHub files info
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
    
    private void showAppDetails(AppInformation app) {
        m_statusLabel.setText("Loading details for " + app.getName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        // Create details dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(app.getName());
        dialog.setHeaderText("Application Details");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #2b2b2b;");
        content.setPrefWidth(600);
        
        // App header with icon
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
                        Label noReleasesLabel = 
                            new Label("No releases found");
                        noReleasesLabel.setStyle("-fx-text-fill: #888888;");
                        content.getChildren().add(noReleasesLabel);
                    } else {
                        VBox releasesBox = new VBox(10);
                        for (AppRelease release : releases) {
                            HBox releaseItem = createReleaseItem(release);
                            releasesBox.getChildren().add(releaseItem);
                        }
                        content.getChildren().add(releasesBox);
                    }
                    
                    m_statusLabel.setText("Ready");
                    m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
                });
            })
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    content.getChildren().remove(loadingLabel);
                    Label errorLabel = 
                        new Label("Error: " + error.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #ff6666;");
                    content.getChildren().add(errorLabel);
                    
                    m_statusLabel.setText("Error loading releases");
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
        
        dialog.show();
    }
    
    private HBox createReleaseItem(AppRelease release) {
        HBox box = new HBox(15);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5px;");
        box.setAlignment(Pos.CENTER_LEFT);
        
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label versionLabel = 
            new Label("Version: " + release.getVersion());
        versionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label tagLabel = 
            new Label("Tag: " + release.getTagName());
        tagLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        
        Label sizeLabel = 
            new Label("Size: " + formatSize(release.getSize()));
        sizeLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        
        infoBox.getChildren().addAll(versionLabel, tagLabel, sizeLabel);
        
        Button installBtn = new Button("Install");
        installBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                            "-fx-padding: 5px 15px; -fx-background-radius: 5px;");
        installBtn.setOnAction(e -> installRelease(release));
        
        box.getChildren().addAll(infoBox, installBtn);
        return box;
    }
    
    private void installApp(AppInformation app) {
        m_statusLabel.setText("Fetching latest release for " + app.getName() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        m_releasesFetcher.fetchReleasesForApp(app)
            .thenAccept(releases -> {
                Platform.runLater(() -> {
                    if (!releases.isEmpty()) {
                        AppRelease latestRelease = releases.get(0);
                        installRelease(latestRelease);
                    } else {
                        m_statusLabel.setText("No releases found for " + app.getName());
                        m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                    }
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
    
    private void installRelease(AppRelease release) {
        m_statusLabel.setText("Installing " + release.getAppInfo().getName() + 
                             " version " + release.getVersion() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        // TODO: Implement actual download and installation logic
        // This would:
        // 1. Download the release JAR from GitHub
        // 2. Verify integrity (checksum)
        // 3. Load the JAR dynamically
        // 4. Instantiate the IWidowApp implementation
        // 5. Register it with NetnotesWidow
        
        System.out.println("Installing from: " + release.getDownloadUrl());
        
        // Simulate installation
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // Simulate download time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, TaskUtils.getVirtualExecutor())
        .thenRun(() -> {
            Platform.runLater(() -> {
                m_statusLabel.setText("Successfully installed " + 
                    release.getAppInfo().getName() + " version " + release.getVersion());
                m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
            });
        })
        .exceptionally(error -> {
            Platform.runLater(() -> {
                m_statusLabel.setText("Installation failed: " + error.getMessage());
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
        //TODO: Cancel pending oper
        // Cancel any pending operations
       // m_appsLoader.shutdown();
      //  m_releasesFetcher.shutdown();
      return CompletableFuture.completedFuture(null);
    }
}
