package io.netnotes.gui.fx.display.contentManager;

import io.netnotes.engine.noteBytes.NoteBytes;
import javafx.stage.Stage;

public interface AppManagerInterface {
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