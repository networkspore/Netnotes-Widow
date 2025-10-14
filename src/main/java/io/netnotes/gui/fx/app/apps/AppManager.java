package io.netnotes.gui.fx.app.apps;


import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.github.GitHubInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class AppManager extends AppBox {
    public final static NoteBytes ID = new NoteBytes("App Manager");
    public final static String NAME = "App Manager";
    private final ExecutorService m_execService;
    private final GitHubInfo m_gitHubInfo;
    
    private VBox m_mainContainer;
    private ScrollPane m_appsScrollPane;
    private VBox m_appsListBox;
    private Label m_statusLabel;
    private Button m_refreshButton;
    
    private List<AppInformation> m_availableApps;
    private AvailableAppsLoader m_appsLoader;
    private AppReleasesFetcher m_releasesFetcher;
    
    public AppManager(DoubleProperty contentWidth, DoubleProperty contentHeight, 
                     GitHubInfo gitHubInfo, ExecutorService execService) {
        super(ID, NAME, contentWidth, contentHeight);
        m_gitHubInfo = gitHubInfo;
        m_execService = execService;
        m_appsLoader = new AvailableAppsLoader(gitHubInfo, execService);
        m_releasesFetcher = new AppReleasesFetcher(execService);
    }
    
    @Override
    protected void initialize() {
        m_mainContainer = new VBox(15);
        m_mainContainer.setPadding(new Insets(20));
        
        // Header
        HBox headerBox = createHeader();
        
        // Apps list
        m_appsListBox = new VBox(10);
        m_appsListBox.setPadding(new Insets(10));
        
        m_appsScrollPane = new ScrollPane(m_appsListBox);
        m_appsScrollPane.setFitToWidth(true);
        m_appsScrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        VBox.setVgrow(m_appsScrollPane, Priority.ALWAYS);
        
        // Status label
        m_statusLabel = new Label("Loading available apps...");
        m_statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        
        m_mainContainer.getChildren().addAll(headerBox, m_appsScrollPane, m_statusLabel);
        this.setCenter(m_mainContainer);
        
        // Load apps
        loadAvailableApps();
    }
    
    private HBox createHeader() {
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label header = new Label("Application Manager");
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
        m_appsListBox.getChildren().clear();
        m_refreshButton.setDisable(true);
        
        m_appsLoader.loadAvailableApps()
            .thenAccept(apps -> {
                javafx.application.Platform.runLater(() -> {
                    m_availableApps = apps;
                    displayApps(apps);
                    m_statusLabel.setText("Found " + apps.size() + " available applications");
                    m_refreshButton.setDisable(false);
                });
            })
            .exceptionally(error -> {
                javafx.application.Platform.runLater(() -> {
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
            ImageView iconView = new ImageView(app.getIcon());
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
        
        Button viewReleasesBtn = new Button("View Releases");
        viewReleasesBtn.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: #ffffff; " +
                                "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        viewReleasesBtn.setOnAction(e -> showReleases(app));
        
        Button installLatestBtn = new Button("Install Latest");
        installLatestBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: #ffffff; " +
                                 "-fx-padding: 6px 12px; -fx-background-radius: 5px;");
        installLatestBtn.setOnAction(e -> installLatestVersion(app));
        
        actionBox.getChildren().addAll(viewReleasesBtn, installLatestBtn);
        
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
    
    private void showReleases(AppInformation app) {
        // Create a dialog to show releases
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Releases for " + app.getName());
        dialog.setHeaderText("Available releases");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #2b2b2b;");
        
        Label loadingLabel = new Label("Loading releases...");
        loadingLabel.setStyle("-fx-text-fill: #cccccc;");
        content.getChildren().add(loadingLabel);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Style the dialog
        dialog.getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
        
        // Load releases
        m_releasesFetcher.fetchReleasesForApp(app)
            .thenAccept(releases -> {
                javafx.application.Platform.runLater(() -> {
                    content.getChildren().clear();
                    
                    if (releases.isEmpty()) {
                        Label noReleasesLabel = new Label("No releases found");
                        noReleasesLabel.setStyle("-fx-text-fill: #888888;");
                        content.getChildren().add(noReleasesLabel);
                    } else {
                        for (AppRelease release : releases) {
                            HBox releaseBox = createReleaseItem(release);
                            content.getChildren().add(releaseBox);
                        }
                    }
                });
            })
            .exceptionally(error -> {
                javafx.application.Platform.runLater(() -> {
                    content.getChildren().clear();
                    Label errorLabel = new Label("Error: " + error.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #ff6666;");
                    content.getChildren().add(errorLabel);
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
        
        Label versionLabel = new Label("Version: " + release.getVersion());
        versionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Label tagLabel = new Label("Tag: " + release.getTagName());
        tagLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        
        Label sizeLabel = new Label("Size: " + formatSize(release.getSize()));
        sizeLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        
        infoBox.getChildren().addAll(versionLabel, tagLabel, sizeLabel);
        
        Button downloadBtn = new Button("Download");
        downloadBtn.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: #ffffff; " +
                            "-fx-padding: 5px 10px; -fx-background-radius: 5px;");
        downloadBtn.setOnAction(e -> downloadRelease(release));
        
        box.getChildren().addAll(infoBox, downloadBtn);
        return box;
    }
    
    private void installLatestVersion(AppInformation app) {
        m_statusLabel.setText("Fetching latest release for " + app.getName() + "...");
        
        m_releasesFetcher.fetchReleasesForApp(app)
            .thenAccept(releases -> {
                javafx.application.Platform.runLater(() -> {
                    if (!releases.isEmpty()) {
                        AppRelease latestRelease = releases.get(0);
                        downloadRelease(latestRelease);
                    } else {
                        m_statusLabel.setText("No releases found for " + app.getName());
                    }
                });
            })
            .exceptionally(error -> {
                javafx.application.Platform.runLater(() -> {
                    m_statusLabel.setText("Error: " + error.getMessage());
                    m_statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
                });
                return null;
            });
    }
    
    private void downloadRelease(AppRelease release) {
        m_statusLabel.setText("Downloading " + release.getAppInfo().getName() + 
                             " version " + release.getVersion() + "...");
        m_statusLabel.setStyle("-fx-text-fill: #4a90e2; -fx-font-size: 12px;");
        
        // TODO: Implement actual download logic
        // This would use UrlStreamHelpers to download the file
        System.out.println("Downloading from: " + release.getDownloadUrl());
        
        // Simulate download completion
        javafx.application.Platform.runLater(() -> {
            m_statusLabel.setText("Downloaded " + release.getAppInfo().getName() + 
                                 " version " + release.getVersion());
            m_statusLabel.setStyle("-fx-text-fill: #5cb85c; -fx-font-size: 12px;");
        });
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    @Override
    protected void onHeightChanged(double newHeight) {
        if (m_appsScrollPane != null) {
            m_appsScrollPane.setPrefHeight(newHeight - 120);
        }
    }
    
    @Override
    public void shutdown() {
        // Cleanup if needed
    }
}