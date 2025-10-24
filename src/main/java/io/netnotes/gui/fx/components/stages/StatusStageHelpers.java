package io.netnotes.gui.fx.components.stages;

import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class StatusStageHelpers {

    public static Scene getTransitionScene(Image icon15, Image windowIcon100, String title, String statusMessage) 
    {
        Label newTitleLbl = new Label(title);
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        ImageView barIconView = new ImageView(icon15);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);

        HBox newTopBar = new HBox(barIconView, newTitleLbl);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(10, 8, 10, 10));
        newTopBar.setId("topBar");

        
        ImageView btnImageView = new ImageView(windowIcon100);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button(title);
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(FxResourceFactory.mainFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);
        imageButton.setGraphicTextGap(20);


        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text statusTxt = new Text(statusMessage);
        statusTxt.setFill(FxResourceFactory.txtColor);
        statusTxt.setFont(FxResourceFactory.txtFont);

        VBox bodyVBox = new VBox(imageBox, statusTxt);

        VBox.setVgrow(bodyVBox, Priority.ALWAYS);
        VBox.setMargin(bodyVBox, new Insets(0, 20, 20, 20));

        VBox layoutVBox = new VBox(newTopBar, bodyVBox);

        Scene statusScene = new Scene(layoutVBox, 420, 215);
        statusScene.setFill(null);
        statusScene.getStylesheets().add("/css/startWindow.css");
        
        return statusScene;

    }
}
