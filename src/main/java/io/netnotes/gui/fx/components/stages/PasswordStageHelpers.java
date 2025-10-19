package io.netnotes.gui.fx.components.stages;

import java.util.function.Consumer;

import io.netnotes.gui.fx.components.fields.PassField;
import io.netnotes.gui.fx.components.hboxes.TopBar;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.application.Platform;
import javafx.event.ActionEvent;
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
import javafx.stage.StageStyle;
import javafx.event.EventHandler;

public class PasswordStageHelpers {
    public static void enterPassword(String heading, String title,Image smallIcon15, Image windowIcon100, Stage stage, 
        EventHandler<ActionEvent> closeEvent, Consumer<PassField> enterEvent)
    {
        stage.setTitle(title);
        stage.setResizable(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(windowIcon100);

    
        Button closeBtn = new Button();
        TopBar titleBox = new TopBar(smallIcon15, heading + " - " + title, closeBtn, stage);

    ImageView btnImageView = new ImageView(windowIcon100);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button(heading);
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(FxResourceFactory.mainFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);
        imageButton.setGraphicTextGap(20);


        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);


        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(FxResourceFactory.txtColor);
        passwordTxt.setFont(FxResourceFactory.txtFont);

        PassField passwordField = new PassField();
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene passwordScene = new Scene(layoutVBox, FxResourceFactory.STAGE_WIDTH, FxResourceFactory.STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add(FxResourceFactory.DEFAULT_CSS);
       
        stage.setScene(passwordScene);


        closeBtn.setOnAction(closeEvent);

    

        passwordField.setOnAction(enterEvent);
        Platform.runLater(()->passwordField.requestFocus());
        passwordScene.focusOwnerProperty().addListener((_, _, _)->{
            
            Platform.runLater(()->passwordField.requestFocus());
            
        });
        stage.show();
        stage.centerOnScreen();
        stage.setOnCloseRequest((_)->{
            closeBtn.fire();
        });
    }


   public static void createPassword(String heading, String title,Image smallIcon15, Image windowIcon100, Stage stage, 
        EventHandler<ActionEvent> closeEvent, Text passwordTxt, PassField passwordField)
    {
        stage.setTitle(title);
        stage.setResizable(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(windowIcon100);

      
        Button closeBtn = new Button();
        TopBar titleBox = new TopBar(smallIcon15, heading + " - " + title, closeBtn, stage);

    
        ImageView btnImageView = new ImageView(windowIcon100);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button(heading);
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(FxResourceFactory.mainFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);
        imageButton.setGraphicTextGap(20);


        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);


        passwordTxt.setFill(FxResourceFactory.txtColor);
        passwordTxt.setFont(FxResourceFactory.txtFont);

        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(0, 0, 0, 0));

        Button clickRegion = new Button();
        clickRegion.setPrefWidth(Double.MAX_VALUE);
        clickRegion.setId("transparentColor");
        clickRegion.setPrefHeight(500);

        clickRegion.setOnAction(_ -> {
            passwordField.requestFocus();

        });

        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox, clickRegion);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene passwordScene = new Scene(layoutVBox, FxResourceFactory.STAGE_WIDTH, FxResourceFactory.STAGE_HEIGHT);
        passwordScene.getStylesheets().add(FxResourceFactory.DEFAULT_CSS);
        stage.setScene(passwordScene);
        closeBtn.setOnAction(closeEvent);

        Platform.runLater(()->passwordField.requestFocus());
        passwordScene.focusOwnerProperty().addListener((_, _, _)->{
            
            Platform.runLater(()->passwordField.requestFocus());
            
        });
        stage.show();
        stage.centerOnScreen();
        stage.setOnCloseRequest((_)->{
            closeBtn.fire();
        });
    }
}
