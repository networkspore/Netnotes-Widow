package io.netnotes.gui.fx.app.apps.appManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.gui.fx.app.NetnotesWidow;
import io.netnotes.gui.fx.components.stages.tabManager.ContentBox;
import io.netnotes.gui.fx.components.stages.tabManager.SideBarButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.scene.image.Image;

/**
 * AppManager implementation as an IWidowApp.
 * Manages installation, updates, and removal of other widow apps.
 */
public class AppManager implements NetnotesWidow.IWidowApp {
    
    private static final String APP_NAME = "App Manager";
    private final NoteBytesReadOnly m_appId;
    
    private AppDataInterface m_appData;
    private NetnotesWidow.TabManagerInterface m_tabManager;
    
    private SideBarButton m_sideBarButton;
    
    private GitHubInfo m_gitHubInfo;
    
    private Map<NoteBytes,ContentBox> m_openAppBoxes = new ConcurrentHashMap<>();

    public AppManager() {
        m_appId = new NoteBytesReadOnly(new NoteBytes("AppManager"));
        
        // Setup GitHub info for app discovery
        m_gitHubInfo = FxResourceFactory.GITHUB_PROJECT_INFO;
    }
    
    @Override
    public NoteBytesReadOnly getAppId() {
        return m_appId;
    }
    
    @Override
    public String getName() {
        return APP_NAME;
    }
    

    @Override
    public void init(AppDataInterface appDataInterface, NetnotesWidow.TabManagerInterface tabManagerInterface) {
        m_appData = appDataInterface;
        m_tabManager = tabManagerInterface;
        
        // Create sidebar button with store icon
        Image appIcon = new Image(FxResourceFactory.WIDOW120);
        m_sideBarButton = new SideBarButton(appIcon, APP_NAME);
        
        // Setup button action
        m_sideBarButton.setOnAction(e -> openMainTab());
        
    }
    
    @Override
    public SideBarButton getSideBarButton() {
        return m_sideBarButton;
    }
    
 
    
    /**
     * Open or focus the main App Manager tab.
     */
    private void openMainTab() {
        NoteBytes tabId = AppManagerBox.ID;
        
        if (!m_tabManager.containsTab(tabId)) {
            m_tabManager.addTab(tabId, APP_NAME, new AppManagerBox(m_tabManager.getStage(), m_gitHubInfo));
        } else {
            m_tabManager.setCurrentTab(tabId);
        }
    }
    
    /**
     * Open app details in a new tab.
    
    public void openAppDetails(AppInformation appInfo) {
        Platform.runLater(() -> {
            NoteBytes tabId = new NoteBytes("app_" + appInfo.getName().replaceAll("\\s+", "_"));
            
            if (!m_tabManager.containsTab(tabId)) {
                AppBox detailsBox = new AppDetailsBox(
                    m_tabManager.getStage(),
                    m_tabManager.contentWidthProperty(),
                    m_tabManager.contentHeightProperty(),
                    appInfo,
                    m_appData.getExecService()
                );
                
                m_tabManager.addTab(tabId, appInfo.getName(), detailsBox);
            } else {
                m_tabManager.setCurrentTab(tabId);
            }
        });
    } */

    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for(Map.Entry<NoteBytes,ContentBox> entry : m_openAppBoxes.entrySet()){
            futures.add(entry.getValue().shutdown());
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
