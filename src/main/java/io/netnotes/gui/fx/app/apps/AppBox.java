package io.netnotes.gui.fx.app.apps;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import javafx.beans.property.DoubleProperty;
import javafx.scene.layout.BorderPane;

public class AppBox extends BorderPane{
    
    protected final DoubleProperty contentWidth;
    protected final DoubleProperty contentHeight;

    private final NoteBytesReadOnly m_appId;
    private final String m_name;

    public AppBox(NoteBytes appId, String name, DoubleProperty contentWidth, DoubleProperty contentHeight){
        super();
        m_appId = new NoteBytesReadOnly(appId);
        m_name = name;
        setId("darkBox");
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        
        // Listen to size changes - all AppBox instances read from same properties
        contentWidth.addListener((obs, old, newVal) -> onWidthChanged(newVal.doubleValue()));
        contentHeight.addListener((obs, old, newVal) -> onHeightChanged(newVal.doubleValue()));
        
       // this.setStyle("-fx-background-color: #1e1e1e;");
        initialize();
    }

    public NoteBytes getAppId(){
        return m_appId;
    }

    public String getName(){
        return m_name;
    }

    public void shutdown(){
      
    }

    // Abstract method for subclasses to implement initialization
    protected void initialize(){

    }
    
    // Called when the content width changes
    protected void onWidthChanged(double newWidth) {
        // Subclasses can override to handle width changes
    }
    
    // Called when the content height changes
    protected void onHeightChanged(double newHeight) {
        // Subclasses can override to handle height changes
    }
    
}


