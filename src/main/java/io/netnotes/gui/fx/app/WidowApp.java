package io.netnotes.gui.fx.app;

import java.io.IOException;

import io.netnotes.engine.noteFiles.SettingsData;
import io.netnotes.gui.fx.components.notifications.Alerts;
import io.netnotes.gui.fx.components.stages.PasswordStageHelpers;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public class WidowApp extends Application {

    Stage m_appStage = null;
    
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
            Alerts.showAndWaitErrorAlert("Critical Failure", e.toString(), null, ButtonType.CLOSE);
            shutdownNow();
        }
    }

   
    private void login(Stage appStage){
         PasswordStageHelpers.enterPassword("Login - " + FxResourceFactory.APP_NAME, FxResourceFactory.icon, 
            FxResourceFactory.logo, appStage, 
            onClose->{ shutdownNow(); }, 
            onEnter->{
                
            }
        );
    }

    private void intializeSettings(Stage appStage){

    }


    public static void shutdownNow() {
        Platform.exit();
        System.exit(0);
    }

}
