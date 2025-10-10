package io.netnotes.gui.fx.components.stages;

import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.components.TopBar;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.event.EventHandler;

public class PasswordStageHelpers {
    public static void enterPassword(String title,Image smallIcon15, Image windowIcon100, Stage appStage, 
        EventHandler<ActionEvent> closeEvent, EventHandler<ActionEvent> enterEvent)
    {
        appStage.setTitle(title);
        appStage.setResizable(false);
        appStage.initStyle(StageStyle.UNDECORATED);
        appStage.getIcons().add(windowIcon100);

        Button closeBtn = new Button();
        TopBar titleBox = new TopBar(smallIcon15, title, closeBtn, appStage);

    
        ImageView btnImageView = new ImageView(windowIcon100);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button("Netnotes");
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(FxResourceFactory.mainFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(FxResourceFactory.txtColor);
        passwordTxt.setFont(FxResourceFactory.txtFont);

        PasswordField passwordField = new PasswordField();
        passwordField.setFont(FxResourceFactory.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(20, 0, 0, 0));

        Button clickRegion = new Button();
        clickRegion.setPrefWidth(Double.MAX_VALUE);
        clickRegion.setId("transparentColor");
        clickRegion.setPrefHeight(500);

        clickRegion.setOnAction(e -> {
            passwordField.requestFocus();

        });

        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox, clickRegion);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene passwordScene = new Scene(layoutVBox, FxResourceFactory.STAGE_WIDTH, FxResourceFactory.STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        appStage.setScene(passwordScene);


        closeBtn.setOnAction(closeEvent);

    

        passwordField.setOnAction(enterEvent);
        Platform.runLater(()->passwordField.requestFocus());
        passwordScene.focusOwnerProperty().addListener((obs, oldval, newval)->{
            
            Platform.runLater(()->passwordField.requestFocus());
            
        });
        appStage.show();
        appStage.centerOnScreen();
    }


}
