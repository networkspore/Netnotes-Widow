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
import io.netnotes.engine.noteFiles.notePath.NotePath;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.contentManager.AppBox;
import io.netnotes.gui.fx.display.contentManager.AppManagerInterface;
import io.netnotes.gui.fx.display.contentManager.AppManagerStage;
import io.netnotes.gui.fx.display.contentManager.IApp;
import javafx.application.HostServices;
import javafx.application.Application.Parameters;
import javafx.stage.Stage;

public class NetnotesWidow extends AppData {

    public static final String NAME = "Netnotes";

    private FxApplicationInterface m_appInterface;
    private final Stage m_appStage;
    private AppManagerStage m_appManagerStage;

    private Map<NoteBytesReadOnly, IApp> m_widowApps = new ConcurrentHashMap<>();

    private boolean m_isStarted = false;
    private boolean m_isShuttingDown = false;

    //receives an appstage that was formerly a password stage
    //settings data contains the information to startup the appData encrypted registry, created from the password stage
    public NetnotesWidow(SettingsData settingsData, Stage appStage){
        super(settingsData);
        m_appStage = appStage;
 
       
    }

    public void start(FxApplicationInterface fxInterface){
        if(!m_isStarted){
            m_isStarted = true;
            m_appInterface = fxInterface;
            m_appManagerStage = new AppManagerStage(m_appStage, NAME, FxResourceFactory.iconImage15, 
                FxResourceFactory.logoImage256, ()->stop());
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
            for (IApp app : m_widowApps.values()) {
                futures.add(app.shutdown(progressWriter));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(_ -> super.shutdown(progressWriter))
                .handle((_, ex) -> {
                    StreamUtils.safeClose(progressWriter);

                    if (ex != null) {
                        m_isShuttingDown = false;
                        System.err.println(ex.getMessage());
                        if (ex.getCause() != null)
                            System.err.println(ex.getCause());
                    }

                    shutdownNow();
                    return null;
                });
        }else{
            return CompletableFuture.failedFuture(new IllegalStateException("Stop in progress"));
        }
    }

      /**
     * Register a new IApp with the system.
     * The app will be initialized and added to the sidebar.
     * 
     * @param app The app to register
     * @return true if successfully added, false if app already exists
     */
    protected boolean addApp(IApp app){
        NoteBytes appId = app.getAppId();


        if(appId != null && !m_widowApps.containsKey(appId)){
            NoteBytesReadOnly staticAppId = app.getAppId().copy();
            
            // Initialize the app with its interfaces
            app.init(
                getAppDataInterface(staticAppId),
                getTabManagerInterface(staticAppId)
            );
   
            // Add sidebar button
            m_appManagerStage.getSideBar().addButton(app.getSideBarButton());
            
            // Store app reference
            m_widowApps.put(staticAppId, app);
            
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
        IApp app = m_widowApps.remove(appId);
        
        if (app != null) {
            // Remove all tabs for this app
            m_appManagerStage.removeTabsByParentId(appId);
            
            // Remove sidebar button
            m_appManagerStage.getSideBar().removeButton(app.getSideBarButton());

            // Shutdown the app
            NoteStringArrayReadOnly path = new NoteStringArrayReadOnly(appId.copy());
    
            return app.shutdown(progressWriter).thenCompose((_)->{
                if(deleteFiles){
                    return getNoteFileService().deleteNoteFilePath(path, deleteFiles, progressWriter).thenApply(_->{
                        return null;
                    });
                }else{
                    return getNoteFileService().prepareForShutdown(path, true);
                }
            });
        }

        
        return CompletableFuture.completedFuture(null);
    }

    private AppManagerInterface getTabManagerInterface(NoteBytesReadOnly appId){
        return new AppManagerInterface(){

            @Override
            public void addTab(NoteBytes tabId,String tabName, AppBox tabBox) {
                m_appManagerStage.addTab(tabId, appId, tabName, tabBox);
            }

            @Override
            public void removeTab(NoteBytes tabId) {
                m_appManagerStage.removeTab(tabId, appId);
            }


            @Override
            public boolean containsTab(NoteBytes tabId) {

                return m_appManagerStage.containsTab(tabId, appId);
            }

            @Override
            public void setCurrentTab(NoteBytes tabId) {
                m_appManagerStage.setCurrentTab(tabId, appId);
            }

            @Override
            public Stage getStage() {
                return m_appStage;
            }

            @Override
            public AppBox[] getAppBoxes() {
                return m_appManagerStage.getAppBoxesByParentId(appId);
            }

            @Override
            public AppBox getAppBox(NoteBytes tabId) {
                return m_appManagerStage.getTab(tabId, appId).getAppBox();
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
                return new HostServicesInterface() {

                    @Override
                    public String getCodeBase() {
                        return NetnotesWidow.this.m_appInterface.getHostServices().getCodeBase();
                    }

                    @Override
                    public String getDocumentBase() {
                        return NetnotesWidow.this.m_appInterface.getHostServices().getDocumentBase();
                    }

                    @Override
                    public String resolveURI(String base, String rel) {
                        return NetnotesWidow.this.m_appInterface.getHostServices().resolveURI(base, rel);
                    }

                    @Override
                    public void showDocument(String uri) {
                        NetnotesWidow.this.m_appInterface.getHostServices().showDocument(uri);
                    }
                    
                };
            }
            @Override
            public ExecutorService getExecService(){
                return NetnotesWidow.this.getExecService();
            }
            @Override
            public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path){
                return NetnotesWidow.this.getNoteFileService().getNoteFile(sandboxPath(path));
            }
            @Override
            public CompletableFuture<NotePath> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive, 
                AsyncNoteBytesWriter progressWriter)
            {
                return NetnotesWidow.this.getNoteFileService().deleteNoteFilePath(sandboxPath(path), recursive, progressWriter);
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
     * Interface provided to apps for managing tabs.
     * All operations are sandboxed to only affect the app's own tabs.
     */
    
}
