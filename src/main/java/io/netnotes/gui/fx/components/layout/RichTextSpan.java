package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * Represents a styled span of text within a segment.
 * Allows for inline formatting (bold, italic, colors, etc.) within a single text segment.
 */
public class RichTextSpan {
    private int start;
    private int end;
    private LayoutSegment.StyleProperties style;
    
    public RichTextSpan(int start, int end, LayoutSegment.StyleProperties style) {
        this.start = start;
        this.end = end;
        this.style = style;
    }
    
    public int getStart() { return start; }
    public int getEnd() { return end; }
    public LayoutSegment.StyleProperties getStyle() { return style; }
    
    public void setStart(int start) { this.start = start; }
    public void setEnd(int end) { this.end = end; }
    public void setStyle(LayoutSegment.StyleProperties style) { this.style = style; }
    
    /**
     * Check if this span contains a given position
     */
    public boolean contains(int position) {
        return position >= start && position < end;
    }
    
    /**
     * Get the length of this span
     */
    public int length() {
        return end - start;
    }
    
    /**
     * Load from NoteBytes
     */
    public static RichTextSpan fromNoteBytesObject(NoteBytesObject nbo) {
        if (nbo == null) return null;
        
        NoteBytes startNb = nbo.get("start") != null ? nbo.get("start").getValue() : null;
        NoteBytes endNb = nbo.get("end") != null ? nbo.get("end").getValue() : null;
        NoteBytes styleNb = nbo.get("style") != null ? nbo.get("style").getValue() : null;
        
        if (startNb == null || endNb == null) return null;
        
        int start = startNb.getAsInt();
        int end = endNb.getAsInt();
        
        LayoutSegment.StyleProperties style = styleNb instanceof NoteBytesObject ?
            LayoutSegment.StyleProperties.fromNoteBytesObject((NoteBytesObject) styleNb) :
            new LayoutSegment.StyleProperties();
        
        return new RichTextSpan(start, end, style);
    }
    
    /**
     * Save to NoteBytes
     */
    public NoteBytesObject toNoteBytesObject() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("start", new NoteInteger(start)),
            new NoteBytesPair("end", new NoteInteger(end)),
            new NoteBytesPair("style", style.toNoteBytesObject())
        });
    }
    
    /**
     * Create a copy of this span
     */
    public RichTextSpan copy() {
        LayoutSegment.StyleProperties styleCopy = new LayoutSegment.StyleProperties();
        styleCopy.fontName = style.fontName;
        styleCopy.fontSize = style.fontSize;
        styleCopy.bold = style.bold;
        styleCopy.italic = style.italic;
        styleCopy.textColor = style.textColor;
        
        return new RichTextSpan(start, end, styleCopy);
    }
    
    /**
     * Adjust span positions after text insertion
     */
    public void adjustForInsert(int position, int length) {
        if (position <= start) {
            start += length;
            end += length;
        } else if (position < end) {
            end += length;
        }
    }
    
    /**
     * Adjust span positions after text deletion
     */
    public void adjustForDelete(int position, int length) {
        if (position >= end) {
            // Deletion after span - no change
            return;
        } else if (position + length <= start) {
            // Deletion before span
            start -= length;
            end -= length;
        } else if (position <= start && position + length >= end) {
            // Deletion encompasses entire span - mark for removal
            start = -1;
            end = -1;
        } else if (position <= start && position + length > start) {
            // Deletion overlaps start
            int deletedFromSpan = position + length - start;
            start = position;
            end -= deletedFromSpan;
        } else if (position < end && position + length >= end) {
            // Deletion overlaps end
            end = position;
        } else {
            // Deletion within span
            end -= length;
        }
    }
    
    /**
     * Check if this span should be removed (marked with -1)
     */
    public boolean shouldRemove() {
        return start < 0 || end < 0 || start >= end;
    }
    
    @Override
    public String toString() {
        return String.format("RichTextSpan[%d-%d, bold=%s, italic=%s]",
            start, end, style.bold, style.italic);
    }
}