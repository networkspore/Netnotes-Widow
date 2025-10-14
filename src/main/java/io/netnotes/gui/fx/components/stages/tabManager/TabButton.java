package io.netnotes.gui.fx.components.stages.tabManager;

import io.netnotes.gui.fx.components.buttons.BufferedButton;
import javafx.scene.image.Image;

public class TabButton extends BufferedButton {
 

    public TabButton(Image image, double imageSize, String text){
        super(image, imageSize);
        setupStyle();
    }

    private void setupStyle() {
        this.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; " +
                     "-fx-padding: 10px 15px; -fx-background-radius: 5px; " +
                     "-fx-border-radius: 5px;");
        
        this.setOnMouseEntered(e -> 
            this.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                         "-fx-padding: 10px 15px; -fx-background-radius: 5px; " +
                         "-fx-border-radius: 5px;"));
        
        this.setOnMouseExited(e -> 
            this.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; " +
                         "-fx-padding: 10px 15px; -fx-background-radius: 5px; " +
                         "-fx-border-radius: 5px;"));
        
       
    }
    
   
}
