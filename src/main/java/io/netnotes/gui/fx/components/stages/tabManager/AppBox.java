package io.netnotes.gui.fx.components.stages.tabManager;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import javafx.scene.layout.BorderPane;

public class AppBox extends BorderPane {
    
    private final NoteBytesReadOnly m_appId;
    private final String m_name;
    
    // Track last known size
    protected double lastWidth = 0;
    protected double lastHeight = 0;

    public AppBox(NoteBytes appId, String name) {
        super();
        m_appId = new NoteBytesReadOnly(appId);
        m_name = name;
        setId("darkBox");
        
        initialize();
    }

    public NoteBytes getAppId() {
        return m_appId;
    }

    public String getName() {
        return m_name;
    }

    public void shutdown() {
        // Override in subclasses to cleanup
    }

    // Abstract method for subclasses to implement initialization
    protected void initialize() {
        // Override in subclasses
    }
    
    /**
     * Called by DeferredLayoutManager when size changes.
     * Override this in subclasses to handle resize events.
     */
    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        
        // Only notify if size actually changed
        if (Math.abs(width - lastWidth) > 0.1) {
            lastWidth = width;
            onWidthChanged(width);
        }
        
        if (Math.abs(height - lastHeight) > 0.1) {
            lastHeight = height;
            onHeightChanged(height);
        }
    }
    
    /**
     * Called when the content width changes.
     * Subclasses can override to handle width changes.
     */
    protected void onWidthChanged(double newWidth) {
        // Subclasses can override
    }
    
    /**
     * Called when the content height changes.
     * Subclasses can override to handle height changes.
     */
    protected void onHeightChanged(double newHeight) {
        // Subclasses can override
    }
}