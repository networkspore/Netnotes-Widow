package io.netnotes.gui.fx.app;

import io.netnotes.gui.fx.components.stages.PasswordStageHelpers;
import javafx.application.Application;
import javafx.stage.Stage;

public class WidowApp extends Application {
    
    @Override
    public void start(Stage appStage) {
        initialize(appStage);
    }

    private void initialize(Stage appStage){
       
        PasswordStageHelpers.enterPassword("Login - Netnotes: Widow", null, null, appStage, null, null);
    }
}
