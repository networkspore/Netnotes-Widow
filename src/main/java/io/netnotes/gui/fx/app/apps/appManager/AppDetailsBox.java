package io.netnotes.gui.fx.app.apps.appManager;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.app.apps.AppInformation;
import io.netnotes.gui.fx.components.stages.tabManager.ContentBox;

/**
 * Detailed view for a specific app.
 */
class AppDetailsBox extends ContentBox {
    
    private final AppInformation m_appInfo;

    
    public AppDetailsBox(javafx.stage.Stage stage,
                        AppInformation appInfo,
                        java.util.concurrent.ExecutorService execService) {
        super(new NoteBytes("AppDetails_" + appInfo.getName()), appInfo.getName());
        m_appInfo = appInfo;
    }
    
    @Override
    protected void initialize() {
        javafx.scene.layout.VBox container = new javafx.scene.layout.VBox(20);
        container.setPadding(new javafx.geometry.Insets(30));
        
        // App header
        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(20);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        if (m_appInfo.getIcon() != null) {
            javafx.scene.image.ImageView iconView = 
                new javafx.scene.image.ImageView(m_appInfo.getIcon());
            iconView.setFitWidth(96);
            iconView.setFitHeight(96);
            headerBox.getChildren().add(iconView);
        }
        
        javafx.scene.layout.VBox titleBox = new javafx.scene.layout.VBox(10);
        
        javafx.scene.control.Label nameLabel = 
            new javafx.scene.control.Label(m_appInfo.getName());
        nameLabel.setStyle("-fx-font-size: 32px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        javafx.scene.control.Label descLabel = 
            new javafx.scene.control.Label(m_appInfo.getDescription());
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 16px;");
        descLabel.setWrapText(true);
        
        titleBox.getChildren().addAll(nameLabel, descLabel);
        headerBox.getChildren().add(titleBox);
        
        container.getChildren().add(headerBox);
        
        // Add more details here (screenshots, changelog, permissions, etc.)
        
        this.setCenter(container);
    }
    
}
  
