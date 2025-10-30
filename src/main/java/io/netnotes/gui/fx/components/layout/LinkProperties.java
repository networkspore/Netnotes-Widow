package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * Properties for clickable links in text segments
 */
public class LinkProperties {
    public String url = "";
    public String title = "";
    public boolean visited = false;
    
    public LinkProperties() {}
    
    public LinkProperties(String url) {
        this.url = url;
    }
    
    public LinkProperties(String url, String title) {
        this.url = url;
        this.title = title;
    }
    
    public static LinkProperties fromNoteBytesObject(NoteBytesObject nbo) {
        LinkProperties props = new LinkProperties();
        
        if (nbo == null) return props;
        
        NoteBytes urlNb = nbo.get("url") != null ? nbo.get("url").getValue() : null;
        if (urlNb != null) {
            props.url = urlNb.getAsString();
        }
        
        NoteBytes titleNb = nbo.get("title") != null ? nbo.get("title").getValue() : null;
        if (titleNb != null) {
            props.title = titleNb.getAsString();
        }
        
        NoteBytes visitedNb = nbo.get("visited") != null ? nbo.get("visited").getValue() : null;
        if (visitedNb != null) {
            props.visited = visitedNb.getAsBoolean();
        }
        
        return props;
    }
    
    public NoteBytesObject toNoteBytesObject() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("url", new NoteString(url)),
            new NoteBytesPair("title", new NoteString(title)),
            new NoteBytesPair("visited", new NoteBoolean(visited))
        });
    }
}