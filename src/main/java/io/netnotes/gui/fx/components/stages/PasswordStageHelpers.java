package io.netnotes.gui.fx.components.stages;

import java.util.concurrent.ExecutorService;

import io.netnotes.engine.noteBytes.NoteBytesSecure;

import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.components.ImageButton;
import io.netnotes.gui.fx.components.PassField;
import io.netnotes.gui.fx.components.TopBar;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tooltip;
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

        PassField passwordField = new PassField();
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


    public static void createPassword(Stage passwordStage, String topTitle, Image windowLogo, Image mainLogo, Button closeBtn, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {
        
        passwordStage.setTitle(topTitle);

      
        HBox titleBox = new TopBar(FxResourceFactory.icon, topTitle, closeBtn, passwordStage);

        ImageButton imageBtn = new ImageButton(mainLogo, "Password");
        imageBtn.setGraphicTextGap(20);
        HBox imageBox = new HBox(imageBtn);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Create password:");
        passwordTxt.setFill(FxResourceFactory.txtColor);
        passwordTxt.setFont(FxResourceFactory.txtFont);

        PasswordField passwordField = new PasswordField();

        passwordField.setFont(FxResourceFactory.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        PasswordField createPassField2 = new PasswordField();
        HBox.setHgrow(createPassField2, Priority.ALWAYS);
        createPassField2.setId("passField");

        Button enterButton = new Button("[enter]");
        enterButton.setId("toolBtn");

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(0, 10,0,0));



        VBox bodyBox = new VBox(passwordBox);
        VBox.setMargin(bodyBox, new Insets(5, 10, 0, 20));
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        VBox passwordVBox = new VBox(titleBox, imageBox, bodyBox);

        Scene passwordScene = new Scene(passwordVBox, FxResourceFactory.STAGE_WIDTH, FxResourceFactory.STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        passwordStage.setScene(passwordScene);

        closeBtn.setOnAction(e -> {
            passwordField.setText("");
            createPassField2.setText("");
            passwordStage.close();
        });

        Text reenterTxt = new Text("Confirm password:");
        reenterTxt.setFill(FxResourceFactory.txtColor);
        reenterTxt.setFont(FxResourceFactory.txtFont);

  

        Button enter2 = new Button("[enter]");
        enter2.setId("toolBtn");

        HBox secondPassBox = new HBox(reenterTxt, createPassField2);
        secondPassBox.setAlignment(Pos.CENTER_LEFT);
        secondPassBox.setPadding(new Insets(0,10,0,0));

        enterButton.setOnAction(e->{

           // String passStr = passwordField.getText();
            // createPassField.setText("");

            bodyBox.getChildren().remove(passwordBox);


            bodyBox.getChildren().add(secondPassBox);

            createPassField2.requestFocus();
            
        });

        passwordField.setOnKeyPressed(e->{
            if(passwordField.getPromptText().length() > 0){
                passwordField.setPromptText("");
            }
        });

        passwordField.textProperty().addListener((obs,oldval,newval) -> {
            
            if(passwordField.getText().length() == 0){
                if(passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().remove(enterButton);
                }
            }else{
                if(!passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().add(enterButton);
                }
            }
        });

        createPassField2.textProperty().addListener((obs,oldval,newval)->{
            if(createPassField2.getText().length() == 0){
                if(secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().remove(enter2);
                }
            }else{
                if(!secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().add(enter2);
                }
            }
        });

        passwordField.setOnAction(e->enterButton.fire());
        
        Tooltip errorToolTip = new Tooltip("Password mis-match");
        

        enter2.setOnAction(e->{
            if (passwordField.getText().equals(createPassField2.getText())) {

                TaskUtils.returnObject( new NoteBytesSecure(passwordField.getText()),execService, onSucceeded);
                createPassField2.setText("");
                passwordField.setText("");
            } else {
                bodyBox.getChildren().clear();
                createPassField2.setText("");
                passwordField.setText("");
                
                
    
                bodyBox.getChildren().add(passwordBox);
                passwordField.requestFocus();

                Point2D p = passwordBox.localToScene(0.0, 0.0);
       

                errorToolTip.show(
                    passwordBox,  
                    p.getX() + passwordBox.getScene().getX() + passwordBox.getScene().getWindow().getX() + passwordBox.getLayoutBounds().getWidth()-150, 
                    (p.getY()+ passwordBox.getScene().getY() + passwordBox.getScene().getWindow().getY())-passwordBox.getLayoutBounds().getHeight()
                );
                
                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                pt.setOnFinished(ptE->{
                    errorToolTip.hide();
                });
                pt.play();
            }
        });

        createPassField2.setOnAction(e->{
            enter2.fire();
        });
        
    }
}
