package io.netnotes.gui.fx.app;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteFiles.SettingsData;
import io.netnotes.engine.noteFiles.SettingsData.InvalidPasswordException;
import io.netnotes.gui.fx.components.fields.PassField;
import io.netnotes.gui.fx.components.notifications.Alerts;
import io.netnotes.gui.fx.components.stages.PasswordStageHelpers;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
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
            shutdownNow();
        }
    }

    private void login(Stage appStage){
        
         PasswordStageHelpers.enterPassword("Login",FxResourceFactory.APP_NAME, FxResourceFactory.iconImage15, 
            FxResourceFactory.logoImage256, appStage, 
            _->{ shutdownNow(); }, 
            passField->{
                try(
                    NoteBytesEphemeral password = passField.getEphemeralPassword();
                ){
                    SettingsData settingsData = SettingsData.readSettings(password);
                    startWidow(appStage, settingsData);
                }catch (InvalidPasswordException e) {
                    
                } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
                    Alerts.showAndWaitErrorAlert("Fatal Error",  "Settings data is inaccessible:\n\t\t" + e.toString(), 
                        appStage,ButtonType.CLOSE);
                    shutdownNow();
                }
            }
        );
    }

    private void intializeSettings(Stage appStage){
        final String createPassString = "Create password:";
        final String repeatPassString = "Repeat password:";

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
                    try(
                        NoteBytesEphemeral firstPassword = passUserData != null && passUserData instanceof NoteBytesEphemeral 
                            ? (NoteBytesEphemeral) passUserData : null;
                    ){
                        if(firstPassword != null && password.equals(firstPassword)){
                            SettingsData settingsData = null;
                            try{
                                settingsData = SettingsData.createSettings(password);
                            }catch(Exception failed){
                                Alerts.showAndWaitErrorAlert("Fatal Error", "Failed to create password:\n\t\t" 
                                    + failed.toString(), appStage,ButtonType.CLOSE);
                                shutdownNow();
                            }
                            if(settingsData != null){
                                startWidow(appStage, settingsData);
                            }
                        }
                    }finally{
                        passField.setUserData(null);
                        passField.escape();
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

    public static void startWidow(Stage appStage, SettingsData settingsData){
        m_widow = new NetnotesWidow(settingsData, appStage, new AppInterface() {
            @Override
            public void shutdownNow(){
                shutdownNow();
            }
            @Override
            public HostServices getHostServices(){
                return getHostServices();
            }
            @Override
            public Parameters getParameters(){
                return getParameters();
            }
        });
        m_widow.start();
    }

    private static void shutdownNow() {
        Platform.exit();
        System.exit(0);
    }

}
