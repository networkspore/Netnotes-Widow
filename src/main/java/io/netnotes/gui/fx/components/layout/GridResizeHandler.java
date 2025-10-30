package io.netnotes.gui.fx.components.layout;

import java.awt.Point;
import java.awt.Rectangle;

import javafx.scene.Cursor;

/**
 * Handles interactive resizing of grid rows and columns
 */
public class GridResizeHandler {
    
    /**
     * Resize handle zone around track boundaries
     */
    private static final int HANDLE_THRESHOLD = 4;
    
    /**
     * Type of resize handle
     */
    public enum HandleType {
        NONE,
        ROW,
        COLUMN,
        CORNER  // Future: resize both row and column
    }
    
    /**
     * Information about a detected resize handle
     */
    public static class ResizeHandle {
        public HandleType type;
        public int trackIndex;  // Which row/column to resize
        public int initialPosition; // Starting position of resize
        public int initialSize;     // Starting size of track
        
        public ResizeHandle(HandleType type, int trackIndex) {
            this.type = type;
            this.trackIndex = trackIndex;
        }
    }
    
    /**
     * Active resize operation
     */
    public static class ResizeOperation {
        public ResizeHandle handle;
        public int startX;
        public int startY;
        public int currentDelta;
        
        public ResizeOperation(ResizeHandle handle, int startX, int startY) {
            this.handle = handle;
            this.startX = startX;
            this.startY = startY;
            this.currentDelta = 0;
        }
    }
    
    /**
     * Detect if point is over a resize handle
     */
    public static ResizeHandle detectHandle(
        Point point,
        GridLayoutEngine.GridLayoutResult gridResult,
        GridLayoutProperties gridProps,
        Rectangle containerBounds
    ) {
        if (gridResult == null) return null;
        
        int x = point.x - containerBounds.x;
        int y = point.y - containerBounds.y;
        
        // Check column handles
        if (gridProps.resizableColumns) {
            for (int i = 0; i < gridResult.columns.size() - 1; i++) {
                GridLayoutEngine.ComputedTrack track = gridResult.columns.get(i);
                int boundaryX = track.getEnd();
                
                if (Math.abs(x - boundaryX) <= HANDLE_THRESHOLD) {
                    ResizeHandle handle = new ResizeHandle(HandleType.COLUMN, i);
                    handle.initialPosition = boundaryX;
                    handle.initialSize = track.size;
                    return handle;
                }
            }
        }
        
        // Check row handles
        if (gridProps.resizableRows) {
            for (int i = 0; i < gridResult.rows.size() - 1; i++) {
                GridLayoutEngine.ComputedTrack track = gridResult.rows.get(i);
                int boundaryY = track.getEnd();
                
                if (Math.abs(y - boundaryY) <= HANDLE_THRESHOLD) {
                    ResizeHandle handle = new ResizeHandle(HandleType.ROW, i);
                    handle.initialPosition = boundaryY;
                    handle.initialSize = track.size;
                    return handle;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get cursor for resize handle
     */
    public static Cursor getCursorForHandle(HandleType type) {
      
        switch (type) {
            case ROW:
                return Cursor.N_RESIZE;
            case COLUMN:
                return Cursor.E_RESIZE;
            case CORNER:
                return Cursor.NW_RESIZE;
            default:
                return Cursor.DEFAULT;
        }
    }
    
    /**
     * Update resize operation with new mouse position
     */
    public static void updateResize(
        ResizeOperation operation,
        int currentX,
        int currentY
    ) {
        switch (operation.handle.type) {
            case COLUMN:
                operation.currentDelta = currentX - operation.startX;
                break;
            case ROW:
                operation.currentDelta = currentY - operation.startY;
                break;
            default:
                break;
        }
    }
    
    /**
     * Apply resize operation to grid layout properties
     * Updates the TrackSize for the resized track
     */
    public static void applyResize(
        ResizeOperation operation,
        GridLayoutProperties gridProps,
        int minTrackSize
    ) {
        int newSize = operation.handle.initialSize + operation.currentDelta;
        newSize = Math.max(newSize, minTrackSize);
        
        switch (operation.handle.type) {
            case COLUMN:
                applyColumnResize(
                    gridProps,
                    operation.handle.trackIndex,
                    newSize
                );
                break;
                
            case ROW:
                applyRowResize(
                    gridProps,
                    operation.handle.trackIndex,
                    newSize
                );
                break;
                
            default:
                break;
        }
    }
    
    /**
     * Apply column resize
     */
    private static void applyColumnResize(
        GridLayoutProperties gridProps,
        int columnIndex,
        int newSize
    ) {
        // Ensure we have enough column definitions
        while (gridProps.columns.size() <= columnIndex) {
            gridProps.columns.add(GridLayoutProperties.TrackSize.auto());
        }
        
        // Update to fixed pixel size
        GridLayoutProperties.TrackSize oldTrack = gridProps.columns.get(columnIndex);
        GridLayoutProperties.TrackSize newTrack = GridLayoutProperties.TrackSize.px(newSize)
            .withMin(oldTrack.getMinSize())
            .withMax(oldTrack.getMaxSize());
        
        gridProps.columns.set(columnIndex, newTrack);
        
        // Adjust next column if it exists and uses fractional sizing
        if (columnIndex + 1 < gridProps.columns.size()) {
            GridLayoutProperties.TrackSize nextTrack = gridProps.columns.get(columnIndex + 1);
            if (nextTrack.getUnit() == GridLayoutProperties.TrackSize.Unit.FR) {
                // Keep fractional sizing - will recalculate on next layout
            }
        }
    }
    
    /**
     * Apply row resize
     */
    private static void applyRowResize(
        GridLayoutProperties gridProps,
        int rowIndex,
        int newSize
    ) {
        // Ensure we have enough row definitions
        while (gridProps.rows.size() <= rowIndex) {
            gridProps.rows.add(GridLayoutProperties.TrackSize.auto());
        }
        
        // Update to fixed pixel size
        GridLayoutProperties.TrackSize oldTrack = gridProps.rows.get(rowIndex);
        GridLayoutProperties.TrackSize newTrack = GridLayoutProperties.TrackSize.px(newSize)
            .withMin(oldTrack.getMinSize())
            .withMax(oldTrack.getMaxSize());
        
        gridProps.rows.set(rowIndex, newTrack);
        
        // Adjust next row if it exists and uses fractional sizing
        if (rowIndex + 1 < gridProps.rows.size()) {
            GridLayoutProperties.TrackSize nextTrack = gridProps.rows.get(rowIndex + 1);
            if (nextTrack.getUnit() == GridLayoutProperties.TrackSize.Unit.FR) {
                // Keep fractional sizing - will recalculate on next layout
            }
        }
    }
    
    /**
     * Draw resize handles (for visual feedback)
     */
    public static void drawHandles(
        java.awt.Graphics2D g2d,
        GridLayoutEngine.GridLayoutResult gridResult,
        GridLayoutProperties gridProps,
        Rectangle containerBounds
    ) {
        if (gridResult == null) return;
        
        java.awt.Color handleColor = new java.awt.Color(100, 100, 100, 128);
        g2d.setColor(handleColor);
        
        // Draw column handles
        if (gridProps.resizableColumns) {
            for (int i = 0; i < gridResult.columns.size() - 1; i++) {
                GridLayoutEngine.ComputedTrack track = gridResult.columns.get(i);
                int x = containerBounds.x + track.getEnd();
                
                g2d.fillRect(
                    x - HANDLE_THRESHOLD / 2,
                    containerBounds.y,
                    HANDLE_THRESHOLD,
                    containerBounds.height
                );
            }
        }
        
        // Draw row handles
        if (gridProps.resizableRows) {
            for (int i = 0; i < gridResult.rows.size() - 1; i++) {
                GridLayoutEngine.ComputedTrack track = gridResult.rows.get(i);
                int y = containerBounds.y + track.getEnd();
                
                g2d.fillRect(
                    containerBounds.x,
                    y - HANDLE_THRESHOLD / 2,
                    containerBounds.width,
                    HANDLE_THRESHOLD
                );
            }
        }
    }
    
    /**
     * Draw resize preview (visual feedback during drag)
     */
    public static void drawResizePreview(
        java.awt.Graphics2D g2d,
        ResizeOperation operation,
        Rectangle containerBounds
    ) {
        if (operation == null) return;
        
        java.awt.Color previewColor = new java.awt.Color(0, 120, 215, 128);
        g2d.setColor(previewColor);
        
        switch (operation.handle.type) {
            case COLUMN:
                int x = containerBounds.x + operation.handle.initialPosition + operation.currentDelta;
                g2d.fillRect(
                    x - 1,
                    containerBounds.y,
                    2,
                    containerBounds.height
                );
                break;
                
            case ROW:
                int y = containerBounds.y + operation.handle.initialPosition + operation.currentDelta;
                g2d.fillRect(
                    containerBounds.x,
                    y - 1,
                    containerBounds.width,
                    2
                );
                break;
                
            default:
                break;
        }
    }
}