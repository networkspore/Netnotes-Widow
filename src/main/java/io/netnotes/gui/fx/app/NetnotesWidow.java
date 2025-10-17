package io.netnotes.gui.fx.app;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.AppData;
import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.HostServicesInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArray;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.SettingsData;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.noteFiles.notePath.NotePath;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.gui.fx.components.stages.tabManager.AppBox;
import io.netnotes.gui.fx.components.stages.tabManager.SideBarButton;
import io.netnotes.gui.fx.components.stages.tabManager.TabManagerStage;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.application.HostServices;
import javafx.application.Application.Parameters;
import javafx.stage.Stage;

public class NetnotesWidow {

    public static final String TITLE = "Netnotes: Widow";

    private final AppInterface m_appInterface;
    private final Stage m_appStage;
    private final SettingsData m_settingsData;
    private final AppData m_appData;
    private final TabManagerStage m_tabManagerStage;

    private Map<NoteBytesReadOnly, IWidowApp> m_widowApps = new ConcurrentHashMap<>();

    private boolean m_isStarted = false;
    private boolean m_isShuttingDown = false;

    //receives an appstage that was formerly a password stage
    //settings data contains the information to startup the appData encrypted registry, created from the password stage
    public NetnotesWidow(SettingsData settingsData, Stage appStage, AppInterface appInterface){
        m_appStage = appStage;
        m_appInterface = appInterface;
        m_settingsData = settingsData;
        m_appData = new AppData(settingsData);
        m_tabManagerStage = new TabManagerStage(m_appStage, TITLE, FxResourceFactory.iconImage15, FxResourceFactory.logoImage256, 
            ()->stop());
    }

    public void start(){
        if(!m_isStarted){
            m_isStarted = true;

            m_tabManagerStage.getSideBar().initializeLayout(m_appStage);
            
            
        }
    }

    public void stop(){
        stop(true);
    }

    public CompletableFuture<Void> stop(boolean force){
        if(!m_isShuttingDown){
   
            m_isShuttingDown = true;

            //TODO: change to shutdown scene & write progress to shutdown scene
            AsyncNoteBytesWriter progressWriter = null;

            //gets a NoteFileServices lock then locks/removes all open notefiles
          
            ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
            // Shutdown all apps
            for (IWidowApp app : m_widowApps.values()) {
                futures.add(app.shutdown(progressWriter));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(v->m_appData.shutdown(progressWriter)).thenRun(()->{
                m_widowApps.clear();
                StreamUtils.safeClose(progressWriter);
                TaskUtils.noDelay(noDelay->{
                    m_appStage.close();
                    // Final shutdown.
                    shutdownNow();
                });
            }).exceptionally(ex->{
                StreamUtils.safeClose(progressWriter);
                m_isShuttingDown = false;
                if(force){
                    TaskUtils.noDelay(noDelay->{
                        m_appStage.close();
                        // Final shutdown.
                        shutdownNow();
                    });
                }
                return null;
            });
        }else{
            return CompletableFuture.failedFuture(new IllegalStateException("Stop in progress"));
        }
    }

      /**
     * Register a new IWidowApp with the system.
     * The app will be initialized and added to the sidebar.
     * 
     * @param iWidowApp The app to register
     * @return true if successfully added, false if app already exists
     */
    protected boolean addApp(IWidowApp iWidowApp){
        NoteBytes appId = iWidowApp.getAppId();


        if(appId != null && !m_widowApps.containsKey(appId)){
            NoteBytesReadOnly staticAppId = iWidowApp.getAppId().copy();
            
            // Initialize the app with its interfaces
            iWidowApp.init(
                getAppDataInterface(staticAppId),
                getTabManagerInterface(staticAppId)
            );
   
            // Add sidebar button
            m_tabManagerStage.getSideBar().addButton(iWidowApp.getSideBarButton());
            
            // Store app reference
            m_widowApps.put(staticAppId, iWidowApp);
            
            return true;
        }
        return false;
    }

    /**
     * Remove an app from the system.
     * Closes all tabs associated with the app and removes from sidebar.
     * 
     * @param appId The ID of the app to remove
     * @return true if removed, false if not found
     */
    protected CompletableFuture<Void> removeApp(NoteBytesReadOnly appId, boolean deleteFiles,  AsyncNoteBytesWriter progressWriter) {
        IWidowApp app = m_widowApps.remove(appId);
        
        if (app != null) {
            // Remove all tabs for this app
            m_tabManagerStage.removeTabsByParentId(appId);
            
            // Remove sidebar button
            m_tabManagerStage.getSideBar().removeButton(app.getSideBarButton());

            // Shutdown the app
            NoteStringArrayReadOnly path = new NoteStringArrayReadOnly(appId.copy());
    
            return app.shutdown(progressWriter).thenCompose((v)->{
                if(deleteFiles){
                    return getNoteFileService().deleteNoteFilePath(path, deleteFiles, progressWriter).thenApply(notePath->{
                        return null;
                    });
                }else{
                    return getNoteFileService().prepareForShutdown(path, true);
                }
            });
        }

        
        return CompletableFuture.completedFuture(null);
    }

    private TabManagerInterface getTabManagerInterface(NoteBytesReadOnly appId){
        return new TabManagerInterface(){

            @Override
            public void addTab(NoteBytes tabId,String tabName, AppBox tabBox) {
                m_tabManagerStage.addTab(tabId, appId, tabName, tabBox);
            }

            @Override
            public void removeTab(NoteBytes tabId) {
                m_tabManagerStage.removeTab(tabId, appId);
            }


            @Override
            public boolean containsTab(NoteBytes tabId) {

                return m_tabManagerStage.containsTab(tabId, appId);
            }

            @Override
            public void setCurrentTab(NoteBytes tabId) {
                m_tabManagerStage.setCurrentTab(tabId, appId);
            }

            @Override
            public Stage getStage() {
                return m_appStage;
            }

            @Override
            public AppBox[] getAppBoxes() {
                return m_tabManagerStage.getAppBoxesByParentId(appId);
            }

            @Override
            public AppBox getAppBox(NoteBytes tabId) {
                return m_tabManagerStage.getTab(tabId, appId).getAppBox();
            }
            
        };
    }

    private AppDataInterface getAppDataInterface(NoteBytesReadOnly appId){
        return new AppDataInterface() {
            @Override
            public void shutdown(){
                stop();
            }
            @Override
            public HostServicesInterface getHostServices(){
                return getHostServices();
            }
            @Override
            public ExecutorService getExecService(){
                return m_appData.getExecService();
            }
            @Override
            public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path){
                return getNoteFileService().getNoteFile(sandboxPath(path));
            }
            @Override
            public CompletableFuture<NotePath> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive, 
                AsyncNoteBytesWriter progressWriter)
            {
                return getNoteFileService().deleteNoteFilePath(sandboxPath(path), recursive, progressWriter);
            }

            /**
             * Sandbox file paths to ensure apps can only access their own directory.
             * If path doesn't start with appId, prepends it.
             */
            private NoteStringArrayReadOnly sandboxPath(NoteStringArrayReadOnly path){
                if(!path.arrayStartsWith(appId)){
                    NoteStringArray noteStringArray = new NoteStringArray(path.get());
                    noteStringArray.add(0, appId);
                    return new NoteStringArrayReadOnly(noteStringArray.get());
                }else{
                    return path;
                }
            }
        };
    }

    

    private CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
    )
    {
        return getNoteFileService().updateFilePathLedgerEncryption(progressWriter, oldPassword, newPassword, batchSize);
    }


    

    private NoteFileService getNoteFileService(){
        return m_appData.getNoteFileService();
    }

    private HostServices getAppHostServices(){
        return m_appInterface.getHostServices();
    }

    private Parameters getAppParameters(){
        return m_appInterface.getParameters();
    }

    private void shutdownNow(){
        m_appInterface.shutdownNow();
    }


    /**
     * Interface that apps must implement to be added to the Widow system.
     */
    public interface IWidowApp {
        /**
         * Unique identifier for this app.
         */
        NoteBytesReadOnly getAppId();
        
        /**
         * Human-readable name for this app.
         */
        String getName();
        
      
        /**
         * Initialize the app with required interfaces.
         * Called once when the app is first registered.
         * 
         * @param appDataInterface Interface for data/file operations
         * @param tabManagerInterface Interface for tab management
         */
        void init(AppDataInterface appDataInterface, TabManagerInterface tabManagerInterface);
        
        /**
         * Get the sidebar button for this app.
         * This button is shown in the sidebar for quick access.
         */
        SideBarButton getSideBarButton();
        
        /**
         * Shutdown the app and clean up resources.
         * Called when the app is removed or the system shuts down.
         */
        CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter);
    }

     /**
     * Interface provided to apps for managing tabs.
     * All operations are sandboxed to only affect the app's own tabs.
     */
    public interface TabManagerInterface {
        /**
         * Add a new tab for this app.
         * 
         * @param tabId Unique identifier for the tab
         * @param tabName Display name for the tab
         * @param tabBox The AppBox to display in the tab
         */
        void addTab(NoteBytes tabId, String tabName, AppBox tabBox);
        
        /**
         * Remove a tab owned by this app.
         * 
         * @param tabId The tab to remove
         */
        void removeTab(NoteBytes tabId);
        
        /**
         * Check if a tab exists and is owned by this app.
         * 
         * @param tabId The tab to check
         * @return true if tab exists and is owned by this app
         */
        boolean containsTab(NoteBytes tabId);
        
        /**
         * Set the currently active tab (if owned by this app).
         * 
         * @param tabId The tab to make active
         */
        void setCurrentTab(NoteBytes tabId);
        
        AppBox[] getAppBoxes();

        AppBox getAppBox(NoteBytes tabId);
        /**
         * Get the main application stage.
         * Needed for registering with DeferredLayoutManager.
         */
        Stage getStage();
    }
}
