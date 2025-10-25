package io.netnotes.gui.fx.app;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteFiles.SettingsData;
import io.netnotes.engine.noteFiles.SettingsData.InvalidPasswordException;
import io.netnotes.gui.fx.components.fields.PassField;
import io.netnotes.gui.fx.components.notifications.Alerts;
import io.netnotes.gui.fx.components.stages.PasswordStageHelpers;
import io.netnotes.gui.fx.components.stages.StatusStageHelpers;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class InitializeApp extends Application {
 
    private static NetnotesWidow m_widow = null;
    
    @Override
    public void start(Stage appStage) {
        try{
            boolean isSettingsData = SettingsData.isSettingsData();
            if(isSettingsData){
                login(appStage);
            }else{
                intializeSettings(appStage);
            }
        }catch(IOException e){
            Alerts.showAndWaitErrorAlert("Critical Failure", e.toString(), appStage, ButtonType.CLOSE);
            e.printStackTrace();
            shutdownNow();
        }
    }

    private void login(Stage appStage){
        Scene verificationScene = StatusStageHelpers.getTransitionScene(FxResourceFactory.iconImage15, 
            FxResourceFactory.logoImage256, "Netnotes", "Verifying...", appStage);

        PasswordStageHelpers.enterPassword("Login",FxResourceFactory.APP_NAME, FxResourceFactory.iconImage15, 
            FxResourceFactory.logoImage256, appStage, 
            _->{ shutdownNow(); }, 
            passField->{
                Scene prevScene = appStage.getScene();
                appStage.setScene(verificationScene);
                appStage.centerOnScreen();
                CompletableFuture<SettingsData> settingsFuture = null;
                try(
                    NoteBytesEphemeral password = passField.getEphemeralPassword();
                ){
                    settingsFuture = SettingsData.readSettings(password, TaskUtils.getVirtualExecutor());
                }

                settingsFuture.whenComplete((settingsData, ex)->{
                    if(ex == null){
                        startWidow(appStage, settingsData);
                    }else{
                        Throwable cause = ex.getCause();
                        TaskUtils.noDelay(_->{
                            passField.escape();
                            appStage.setScene(prevScene);
                            appStage.centerOnScreen();
                            appStage.requestFocus();
                            if(!(cause instanceof InvalidPasswordException)){
                                TaskUtils.noDelay(_->{
                                    Optional<ButtonType> result = Alerts.showAndWaitErrorAlert("Critical Error", 
                                        ex.getMessage() + ":\n\n\t" + cause.toString(), appStage, ButtonType.OK, 
                                        ButtonType.CLOSE);

                                    if(result.isEmpty() || result.get() == ButtonType.CLOSE){
                                        shutdownNow();
                                    }
                                });
                            }
                        });
                       
                    }
                });
            }
        );
    }

    private void intializeSettings(Stage appStage){
        final String createPassString = "Create password:";
        final String repeatPassString = "Repeat password:";
        final Scene transitionScene = StatusStageHelpers.getTransitionScene(FxResourceFactory.iconImage15,
            FxResourceFactory.logoImage256, "Netnotes", "Initializizing...", appStage);
        final PassField passField = new PassField();
        final Text passText = new Text(createPassString);

        PasswordStageHelpers.createPassword("Create Password",FxResourceFactory.APP_NAME, FxResourceFactory.iconImage15, 
            FxResourceFactory.logoImage256, appStage, 
            _->{
                passField.close();
                Object passUserData = passField.getUserData();
                if(passUserData != null && passUserData instanceof NoteBytesEphemeral){
                    NoteBytesEphemeral firstPassword = (NoteBytesEphemeral) passUserData;
                    firstPassword.close(); 
                    passField.setUserData(null);
                }
                shutdownNow();
        }, passText, passField);

        passField.setOnAction(_->{
            try(NoteBytesEphemeral password = passField.getEphemeralPassword()){
                if(passText.getText().equals(createPassString)){
                    passField.setUserData(password.copy());
                    passText.setText(repeatPassString);
                    passField.clear();
                }else{
                    Object passUserData = passField.getUserData();
                    CompletableFuture<SettingsData> settingsFuture = null;
                    try(
                        NoteBytesEphemeral firstPassword = passUserData != null && passUserData instanceof NoteBytesEphemeral 
                            ? (NoteBytesEphemeral) passUserData : null;
                    ){
                        if(firstPassword != null && password.equals(firstPassword)){
                            appStage.setScene(transitionScene);
                            settingsFuture = SettingsData.createSettings(password, TaskUtils.getVirtualExecutor());
                        }else{
                            settingsFuture = null;
                        }
                    }finally{
                        passField.setUserData(null);
                        passField.escape();
                    }
                    if(settingsFuture != null){
                        settingsFuture.whenComplete((settingsData, ex)->{
                            if(ex != null){
                                Throwable failed = ex.getCause();
                                failed.printStackTrace();
                                TaskUtils.noDelay(_-> {
                                    Optional<ButtonType> result = Alerts.showAndWaitErrorAlert("Critical Error", ex.getMessage() + 
                                        ":\n\t\t" + failed.toString(), appStage,ButtonType.OK, ButtonType.CLOSE);
                                    if(result.isEmpty() || result.get() == ButtonType.CLOSE){
                                        shutdownNow();    
                                    }else{

                                    }
                                });
                        
                            }else{
                                startWidow(appStage, settingsData);
                            }
                        });
                    }         
                }
            }
        });

        passField.setOnEscape(()->{
            Object passUserData = passField.getUserData();
            NoteBytesEphemeral firstPassword = passUserData != null && passUserData instanceof NoteBytesEphemeral ? 
                (NoteBytesEphemeral) passUserData : null;
            if(firstPassword != null){
                firstPassword.close();
                passField.setUserData(null);
            }
            passText.setText(createPassString);
        });

    }

    public NetnotesWidow getWidow(){
        return m_widow;
    }


    public void startWidow(Stage appStage, SettingsData settingsData){
    
        m_widow = new NetnotesWidow(settingsData, appStage);
        Platform.runLater(() -> {
            m_widow.start(new FxApplicationInterface() {
  

                @Override
                public HostServices getHostServices(){
                    return InitializeApp.this.getHostServices();
                }
                @Override
                public Parameters getParameters(){
                    return InitializeApp.this.getParameters();
                }
                @Override
                public void shutdownNow() {
                    InitializeApp.this.shutdownNow();
                }
            });
        });
    }

    public void shutdownNow() {
        System.out.println("Shutting down...");
        try {
            // Try graceful shutdown
            Platform.exit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }

        
    }

}
