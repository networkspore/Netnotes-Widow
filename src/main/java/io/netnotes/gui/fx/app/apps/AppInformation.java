package io.netnotes.gui.fx.app.apps;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.github.GitHubFileInfo;
import javafx.scene.image.Image;

public class AppInformation {

    private NoteBytes m_appId;
    private String m_networkName;
    private Image m_icon;
    private Image m_smallIcon;
    private String m_description;
    private GitHubFileInfo[] m_gitHubFiles;

    public AppInformation(NoteBytes appId, String appName, Image icon, Image smallIcon, String description, GitHubFileInfo... gitHubFiles){
        m_appId = appId;
        m_networkName = appName;
        m_icon = icon;
        m_smallIcon = smallIcon;
        m_description = description;
        m_gitHubFiles = gitHubFiles;
    }

    public String getDescription(){
        return m_description;
    }

    public NoteBytes getAppId(){
        return m_appId;
    }

    public String getName(){
        return m_networkName;
    }

    public Image getIcon(){
        return m_icon;
    }

    public Image getSmallIcon(){
        return m_smallIcon;
    }

    public GitHubFileInfo[] getGitHubFiles(){
        return m_gitHubFiles;
    }

}