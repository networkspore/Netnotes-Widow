package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteDouble;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid/Row/Column layout properties for advanced layout control.
 * Inspired by CSS Grid, Flexbox, and table layout systems.
 */
public class GridLayoutProperties {
    
    /**
     * Layout direction for flex/grid containers
     */
    public enum Direction {
        ROW(0),          // Horizontal
        COLUMN(1),       // Vertical
        ROW_REVERSE(2),
        COLUMN_REVERSE(3);
        
        private final int value;
        
        Direction(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static Direction fromValue(int value) {
            for (Direction d : values()) {
                if (d.value == value) return d;
            }
            return ROW;
        }
        
        public boolean isRow() {
            return this == ROW || this == ROW_REVERSE;
        }
        
        public boolean isColumn() {
            return this == COLUMN || this == COLUMN_REVERSE;
        }
    }
    
    /**
     * How to handle overflow content
     */
    public enum Overflow {
        VISIBLE(0),   // Content overflows container
        HIDDEN(1),    // Content clipped to container
        SCROLL(2),    // Show scrollbars
        AUTO(3),      // Scrollbars only when needed
        WRAP(4);      // Wrap to next row/column
        
        private final int value;
        
        Overflow(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static Overflow fromValue(int value) {
            for (Overflow o : values()) {
                if (o.value == value) return o;
            }
            return VISIBLE;
        }
    }
    
    /**
     * Alignment along main axis (justify-content)
     */
    public enum JustifyContent {
        START(0),
        END(1),
        CENTER(2),
        SPACE_BETWEEN(3),
        SPACE_AROUND(4),
        SPACE_EVENLY(5);
        
        private final int value;
        
        JustifyContent(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static JustifyContent fromValue(int value) {
            for (JustifyContent j : values()) {
                if (j.value == value) return j;
            }
            return START;
        }
    }
    
    /**
     * Alignment along cross axis (align-items)
     */
    public enum AlignItems {
        START(0),
        END(1),
        CENTER(2),
        STRETCH(3),
        BASELINE(4);
        
        private final int value;
        
        AlignItems(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static AlignItems fromValue(int value) {
            for (AlignItems a : values()) {
                if (a.value == value) return a;
            }
            return STRETCH;
        }
    }
    
    /**
     * Track (row/column) size definition
     * Can be fixed pixels, percentage, fraction of remaining space, or auto-sized
     */
    public static class TrackSize {
        public enum Unit {
            PX(0),        // Fixed pixels
            PERCENT(1),   // Percentage of container
            FR(2),        // Fraction of remaining space (like CSS fr)
            AUTO(3),      // Size to content
            MIN_CONTENT(4),  // Minimum content size
            MAX_CONTENT(5);  // Maximum content size
            
            private final int value;
            
            Unit(int value) { this.value = value; }
            public int getValue() { return value; }
            
            public static Unit fromValue(int value) {
                for (Unit u : values()) {
                    if (u.value == value) return u;
                }
                return AUTO;
            }
        }
        
        private final double value;
        private final Unit unit;
        private final double minSize;  // Minimum size constraint
        private final double maxSize;  // Maximum size constraint
        
        public TrackSize(double value, Unit unit, double minSize, double maxSize) {
            this.value = value;
            this.unit = unit;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }
        
        public static TrackSize px(double value) {
            return new TrackSize(value, Unit.PX, 0, Double.MAX_VALUE);
        }
        
        public static TrackSize percent(double value) {
            return new TrackSize(value, Unit.PERCENT, 0, Double.MAX_VALUE);
        }
        
        public static TrackSize fr(double value) {
            return new TrackSize(value, Unit.FR, 0, Double.MAX_VALUE);
        }
        
        public static TrackSize auto() {
            return new TrackSize(0, Unit.AUTO, 0, Double.MAX_VALUE);
        }
        
        public static TrackSize minContent() {
            return new TrackSize(0, Unit.MIN_CONTENT, 0, Double.MAX_VALUE);
        }
        
        public static TrackSize maxContent() {
            return new TrackSize(0, Unit.MAX_CONTENT, 0, Double.MAX_VALUE);
        }
        
        public TrackSize withMin(double min) {
            return new TrackSize(value, unit, min, maxSize);
        }
        
        public TrackSize withMax(double max) {
            return new TrackSize(value, unit, minSize, max);
        }
        
        public double getValue() { return value; }
        public Unit getUnit() { return unit; }
        public double getMinSize() { return minSize; }
        public double getMaxSize() { return maxSize; }
        
        /**
         * Resolve size given container size and available space
         */
        public int resolve(int containerSize, int availableSpace, int contentSize) {
            int resolved;
            
            switch (unit) {
                case PX:
                    resolved = (int) value;
                    break;
                    
                case PERCENT:
                    resolved = (int) (containerSize * value / 100.0);
                    break;
                    
                case FR:
                    // Fraction is resolved during layout phase
                    resolved = 0;
                    break;
                    
                case AUTO:
                case MIN_CONTENT:
                case MAX_CONTENT:
                    resolved = contentSize;
                    break;
                    
                default:
                    resolved = 0;
            }
            
            // Apply constraints
            if (minSize > 0) {
                resolved = Math.max(resolved, (int) minSize);
            }
            if (maxSize < Double.MAX_VALUE) {
                resolved = Math.min(resolved, (int) maxSize);
            }
            
            return resolved;
        }
        
        public static TrackSize fromNoteBytes(NoteBytes nb) {
            if (nb == null) return auto();
            
            if (nb.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesObject nbo = nb.getAsNoteBytesObject();
                
                NoteBytes unitNb = nbo.get("unit") != null ? nbo.get("unit").getValue() : null;
                NoteBytes valueNb = nbo.get("value") != null ? nbo.get("value").getValue() : null;
                NoteBytes minNb = nbo.get("min") != null ? nbo.get("min").getValue() : null;
                NoteBytes maxNb = nbo.get("max") != null ? nbo.get("max").getValue() : null;
                
                Unit unit = unitNb != null ? Unit.fromValue(unitNb.getAsInt()) : Unit.AUTO;
                double value = valueNb != null ? valueNb.getAsDouble() : 0;
                double min = minNb != null ? minNb.getAsDouble() : 0;
                double max = maxNb != null ? maxNb.getAsDouble() : Double.MAX_VALUE;
                
                return new TrackSize(value, unit, min, max);
            }
            
            return auto();
        }
        
        public NoteBytesObject toNoteBytes() {
            return new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair("unit", new NoteInteger(unit.getValue())),
                new NoteBytesPair("value", new NoteDouble(value)),
                new NoteBytesPair("min", new NoteDouble(minSize)),
                new NoteBytesPair("max", new NoteDouble(maxSize))
            });
        }
        
        @Override
        public String toString() {
            switch (unit) {
                case PX: return value + "px";
                case PERCENT: return value + "%";
                case FR: return value + "fr";
                case AUTO: return "auto";
                case MIN_CONTENT: return "min-content";
                case MAX_CONTENT: return "max-content";
                default: return "0px";
            }
        }
    }
    
    /**
     * Gap between rows/columns
     */
    public static class Gap {
        private final int rowGap;
        private final int columnGap;
        
        public Gap(int rowGap, int columnGap) {
            this.rowGap = rowGap;
            this.columnGap = columnGap;
        }
        
        public Gap(int gap) {
            this.rowGap = gap;
            this.columnGap = gap;
        }
        
        public int getRowGap() { return rowGap; }
        public int getColumnGap() { return columnGap; }
        
        public static Gap fromNoteBytes(NoteBytes nb) {
            if (nb == null) return new Gap(0, 0);
            
            if (nb.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesObject nbo = nb.getAsNoteBytesObject();
                
                NoteBytes rowNb = nbo.get("row") != null ? nbo.get("row").getValue() : null;
                NoteBytes colNb = nbo.get("column") != null ? nbo.get("column").getValue() : null;
                
                int row = rowNb != null ? rowNb.getAsInt() : 0;
                int col = colNb != null ? colNb.getAsInt() : 0;
                
                return new Gap(row, col);
            }
            
            // Single value for both
            return new Gap(nb.getAsInt());
        }
        
        public NoteBytesObject toNoteBytes() {
            return new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair("row", new NoteInteger(rowGap)),
                new NoteBytesPair("column", new NoteInteger(columnGap))
            });
        }
    }
    
    // ========== Properties ==========
    
    public Direction direction = Direction.ROW;
    public Overflow overflow = Overflow.VISIBLE;
    public JustifyContent justifyContent = JustifyContent.START;
    public AlignItems alignItems = AlignItems.STRETCH;
    public Gap gap = new Gap(0, 0);
    
    // Row/column definitions
    public List<TrackSize> rows = new ArrayList<>();
    public List<TrackSize> columns = new ArrayList<>();
    
    // Whether tracks (rows/columns) can be resized by user
    public boolean resizableRows = false;
    public boolean resizableColumns = false;
    
    // Minimum size for resizable tracks
    public int minTrackSize = 20;
    
    // ========== Serialization ==========
    
    public static GridLayoutProperties fromNoteBytesObject(NoteBytesObject nbo) {
        GridLayoutProperties props = new GridLayoutProperties();
        
        if (nbo == null) return props;
        
        // Direction
        NoteBytes dirNb = nbo.get("direction") != null ? nbo.get("direction").getValue() : null;
        if (dirNb != null) {
            props.direction = Direction.fromValue(dirNb.getAsInt());
        }
        
        // Overflow
        NoteBytes overflowNb = nbo.get("overflow") != null ? nbo.get("overflow").getValue() : null;
        if (overflowNb != null) {
            props.overflow = Overflow.fromValue(overflowNb.getAsInt());
        }
        
        // Justify content
        NoteBytes justifyNb = nbo.get("justifyContent") != null ? nbo.get("justifyContent").getValue() : null;
        if (justifyNb != null) {
            props.justifyContent = JustifyContent.fromValue(justifyNb.getAsInt());
        }
        
        // Align items
        NoteBytes alignNb = nbo.get("alignItems") != null ? nbo.get("alignItems").getValue() : null;
        if (alignNb != null) {
            props.alignItems = AlignItems.fromValue(alignNb.getAsInt());
        }
        
        // Gap
        NoteBytes gapNb = nbo.get("gap") != null ? nbo.get("gap").getValue() : null;
        if (gapNb != null) {
            props.gap = Gap.fromNoteBytes(gapNb);
        }
        
        // Rows
        NoteBytes rowsNb = nbo.get("rows") != null ? nbo.get("rows").getValue() : null;
        if (rowsNb instanceof NoteBytesArray) {
            NoteBytesArray rowsArray = (NoteBytesArray) rowsNb;
            props.rows.clear();
            for (int i = 0; i < rowsArray.size(); i++) {
                props.rows.add(TrackSize.fromNoteBytes(rowsArray.get(i)));
            }
        }
        
        // Columns
        NoteBytes colsNb = nbo.get("columns") != null ? nbo.get("columns").getValue() : null;
        if (colsNb instanceof NoteBytesArray) {
            NoteBytesArray colsArray = (NoteBytesArray) colsNb;
            props.columns.clear();
            for (int i = 0; i < colsArray.size(); i++) {
                props.columns.add(TrackSize.fromNoteBytes(colsArray.get(i)));
            }
        }
        
        // Resizable flags
        NoteBytes resizeRowsNb = nbo.get("resizableRows") != null ? nbo.get("resizableRows").getValue() : null;
        if (resizeRowsNb != null) {
            props.resizableRows = resizeRowsNb.getAsBoolean();
        }
        
        NoteBytes resizeColsNb = nbo.get("resizableColumns") != null ? nbo.get("resizableColumns").getValue() : null;
        if (resizeColsNb != null) {
            props.resizableColumns = resizeColsNb.getAsBoolean();
        }
        
        // Min track size
        NoteBytes minTrackNb = nbo.get("minTrackSize") != null ? nbo.get("minTrackSize").getValue() : null;
        if (minTrackNb != null) {
            props.minTrackSize = minTrackNb.getAsInt();
        }
        
        return props;
    }
    
    public NoteBytesObject toNoteBytesObject() {
        NoteBytesObject nbo = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("direction", new NoteInteger(direction.getValue())),
            new NoteBytesPair("overflow", new NoteInteger(overflow.getValue())),
            new NoteBytesPair("justifyContent", new NoteInteger(justifyContent.getValue())),
            new NoteBytesPair("alignItems", new NoteInteger(alignItems.getValue())),
            new NoteBytesPair("gap", gap.toNoteBytes()),
            new NoteBytesPair("resizableRows", new NoteBoolean(resizableRows)),
            new NoteBytesPair("resizableColumns", new NoteBoolean(resizableColumns)),
            new NoteBytesPair("minTrackSize", new NoteInteger(minTrackSize))
        });
        
        // Rows
        if (!rows.isEmpty()) {
            NoteBytesArray rowsArray = new NoteBytesArray();
            for (TrackSize row : rows) {
                rowsArray.add(row.toNoteBytes());
            }
            nbo.add("rows", rowsArray);
        }
        
        // Columns
        if (!columns.isEmpty()) {
            NoteBytesArray colsArray = new NoteBytesArray();
            for (TrackSize col : columns) {
                colsArray.add(col.toNoteBytes());
            }
            nbo.add("columns", colsArray);
        }
        
        return nbo;
    }
}