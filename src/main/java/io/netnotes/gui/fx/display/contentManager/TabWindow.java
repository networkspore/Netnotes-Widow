package io.netnotes.gui.fx.display.contentManager;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;

public interface TabWindow {
     /**
     * Add a tab to this window's display
     */
    void displayTab(ContentTab tab);

    /**
     * Remove a tab from this window's display (but don't delete it)
     */
    void undisplayTab(NoteBytesArray tabId);
    

    SimpleObjectProperty<NoteBytesArray> currentIdProperty();


    /**
     * Set which tab is currently active/visible
     */
    void setCurrentTab(NoteBytesArray tabId);
    
    /**
     * Get the JavaFX Stage for this window
     */
    Stage getStage();
    
    /**
     * Check if a screen coordinate is within this window's top bar
     */
    boolean containsPoint(double screenX, double screenY);
    
    /**
     * Get the top bar height for drop detection
     */
    double getTopBarHeight();
    
    /**
     * Show the window
     */
    void show();
    
    /**
     * Close the window
     */
    void close();
    
    /**
     * Check if this is the primary/main window
     */
    boolean isPrimaryWindow();
}