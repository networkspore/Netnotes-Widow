package io.netnotes.gui.fx.display.contentManager;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;

/**
 * Interface that apps must implement to be added to the Widow system.
 */
public interface IApp {
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
    void init(AppDataInterface appDataInterface, AppManagerInterface tabManagerInterface);
    
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

