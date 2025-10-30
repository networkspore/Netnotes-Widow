package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * Properties for positioning an item within a grid layout.
 * Specifies which row/column the item occupies and how many tracks it spans.
 */
public class GridItemProperties {
    
    /**
     * Grid position (1-based, following CSS Grid convention)
     * Value of 0 means auto-placement
     */
    public int column = 0;  // Column start position (1-based)
    public int row = 0;     // Row start position (1-based)
    
    /**
     * How many columns/rows this item spans
     */
    public int columnSpan = 1;
    public int rowSpan = 1;
    
    /**
     * Alignment within the grid cell (optional, overrides container alignment)
     */
    public GridLayoutProperties.AlignItems alignSelf = null;
    public GridLayoutProperties.JustifyContent justifySelf = null;
    
    /**
     * Default constructor for auto-placement
     */
    public GridItemProperties() {
    }
    
    /**
     * Constructor with explicit positioning
     */
    public GridItemProperties(int column, int row) {
        this.column = column;
        this.row = row;
    }
    
    /**
     * Constructor with positioning and spanning
     */
    public GridItemProperties(int column, int row, int columnSpan, int rowSpan) {
        this.column = column;
        this.row = row;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
    }
    
    /**
     * Check if this item uses auto-placement
     */
    public boolean isAutoPlaced() {
        return column == 0 && row == 0;
    }
    
    /**
     * Check if this item spans multiple columns
     */
    public boolean spansMultipleColumns() {
        return columnSpan > 1;
    }
    
    /**
     * Check if this item spans multiple rows
     */
    public boolean spansMultipleRows() {
        return rowSpan > 1;
    }
    
    /**
     * Get the ending column (exclusive)
     */
    public int getColumnEnd() {
        return column + columnSpan;
    }
    
    /**
     * Get the ending row (exclusive)
     */
    public int getRowEnd() {
        return row + rowSpan;
    }
    
    // ========== Serialization ==========
    
    /**
     * Load from NoteBytes object
     */
    public static GridItemProperties fromNoteBytesObject(NoteBytesObject nbo) {
        GridItemProperties props = new GridItemProperties();
        
        if (nbo == null) return props;
        
        // Column position
        NoteBytes columnNb = nbo.get("column") != null ? nbo.get("column").getValue() : null;
        if (columnNb != null) {
            props.column = columnNb.getAsInt();
        }
        
        // Row position
        NoteBytes rowNb = nbo.get("row") != null ? nbo.get("row").getValue() : null;
        if (rowNb != null) {
            props.row = rowNb.getAsInt();
        }
        
        // Column span
        NoteBytes columnSpanNb = nbo.get("columnSpan") != null ? nbo.get("columnSpan").getValue() : null;
        if (columnSpanNb != null) {
            props.columnSpan = columnSpanNb.getAsInt();
        }
        
        // Row span
        NoteBytes rowSpanNb = nbo.get("rowSpan") != null ? nbo.get("rowSpan").getValue() : null;
        if (rowSpanNb != null) {
            props.rowSpan = rowSpanNb.getAsInt();
        }
        
        // Alignment overrides (optional)
        NoteBytes alignSelfNb = nbo.get("alignSelf") != null ? nbo.get("alignSelf").getValue() : null;
        if (alignSelfNb != null) {
            props.alignSelf = GridLayoutProperties.AlignItems.fromValue(alignSelfNb.getAsInt());
        }
        
        NoteBytes justifySelfNb = nbo.get("justifySelf") != null ? nbo.get("justifySelf").getValue() : null;
        if (justifySelfNb != null) {
            props.justifySelf = GridLayoutProperties.JustifyContent.fromValue(justifySelfNb.getAsInt());
        }
        
        return props;
    }
    
    /**
     * Save to NoteBytes object
     */
    public NoteBytesObject toNoteBytesObject() {
        NoteBytesObject nbo = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("column", new NoteInteger(column)),
            new NoteBytesPair("row", new NoteInteger(row)),
            new NoteBytesPair("columnSpan", new NoteInteger(columnSpan)),
            new NoteBytesPair("rowSpan", new NoteInteger(rowSpan))
        });
        
        // Add optional alignment overrides
        if (alignSelf != null) {
            nbo.add("alignSelf", new NoteInteger(alignSelf.getValue()));
        }
        
        if (justifySelf != null) {
            nbo.add("justifySelf", new NoteInteger(justifySelf.getValue()));
        }
        
        return nbo;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GridItem[");
        
        if (isAutoPlaced()) {
            sb.append("auto");
        } else {
            sb.append("col=").append(column);
            sb.append(", row=").append(row);
        }
        
        if (columnSpan > 1) {
            sb.append(", colspan=").append(columnSpan);
        }
        
        if (rowSpan > 1) {
            sb.append(", rowspan=").append(rowSpan);
        }
        
        sb.append("]");
        return sb.toString();
    }
}