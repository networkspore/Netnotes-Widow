package io.netnotes.gui.fx.app;

import io.netnotes.engine.noteFiles.SettingsData;
import javafx.application.HostServices;
import javafx.application.Application.Parameters;
import javafx.stage.Stage;

public class WidowApp {

    private final AppInterface m_appInterface;
    private final Stage m_appStage;
    private final SettingsData m_settingsData;

    private boolean m_isStarted = false;

    public WidowApp(SettingsData settingsData, Stage appStage, AppInterface appInterface){
        m_appStage = appStage;
        m_appInterface = appInterface;
        m_settingsData = settingsData;
    }

    public void start(){
        if(!m_isStarted){
            m_isStarted = true;
            setAppStage();
        }
    }

    public void stop(){

    }

    private void setAppStage(){
        
    }

    public void shutdownNow(){
        m_appInterface.shutdownNow();
    }

   
}
