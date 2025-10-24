package io.netnotes.gui.fx.components.stages;

import io.netnotes.gui.fx.components.hboxes.TopBar;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class StatusStageHelpers {

    public static Scene getTransitionScene(Image icon15, Image windowIcon100, String title, String statusMessage, Stage stage) 
    {
        TopBar topBar = new TopBar(icon15, title, stage);
       
        ImageView btnImageView = new ImageView(windowIcon100);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button(title);
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(FxResourceFactory.HeadingFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);
        imageButton.setGraphicTextGap(20);


        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);


        Text statusText = new Text(statusMessage);
        statusText.setFill(FxResourceFactory.txtColor);
        statusText.setFont(FxResourceFactory.txtFont);

        HBox passwordBox = new HBox(statusText);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(topBar, imageBox, passwordBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene statusScene = new Scene(layoutVBox, FxResourceFactory.STAGE_WIDTH, FxResourceFactory.STAGE_HEIGHT);
        statusScene.setFill(null);
        statusScene.getStylesheets().add(FxResourceFactory.DEFAULT_CSS);
    
        return statusScene;
    }
}
