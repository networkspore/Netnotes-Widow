package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Grid Layout Engine - Handles row/column layout with flexible sizing
 * Supports:
 * - Fixed, percentage, fractional (fr), and auto sizing
 * - Content-based sizing (min-content, max-content)
 * - Gaps between rows/columns
 * - Alignment and justification
 * - Overflow handling
 */
public class GridLayoutEngine {
    
    /**
     * Computed track (row/column) size after layout
     */
    public static class ComputedTrack {
        public int start;      // Start position
        public int size;       // Computed size
        public int contentSize; // Size of content in this track
        public boolean isResizable; // Can user resize this track
        
        public ComputedTrack(int start, int size) {
            this.start = start;
            this.size = size;
            this.contentSize = 0;
            this.isResizable = false;
        }
        
        public int getEnd() {
            return start + size;
        }
    }
    
    /**
     * Result of grid layout computation
     */
    public static class GridLayoutResult {
        public List<ComputedTrack> rows;
        public List<ComputedTrack> columns;
        public int totalWidth;
        public int totalHeight;
        public List<Rectangle> cellBounds; // Bounds for each child
        
        public GridLayoutResult() {
            this.rows = new ArrayList<>();
            this.columns = new ArrayList<>();
            this.cellBounds = new ArrayList<>();
        }
    }
    
    /**
     * Compute grid layout
     */
    public static GridLayoutResult computeLayout(
        LayoutSegment segment,
        NoteBytesArray children,
        int containerWidth,
        int containerHeight,
        GridLayoutProperties gridProps
    ) {
        GridLayoutResult result = new GridLayoutResult();
        
        if (children == null || children.size() == 0) {
            return result;
        }
        
        // Deflate by padding
        Insets padding = segment.getLayout().padding;
        int availableWidth = containerWidth - padding.left - padding.right;
        int availableHeight = containerHeight - padding.top - padding.bottom;
        
        // Determine grid dimensions
        int numRows = gridProps.rows.isEmpty() ? 1 : gridProps.rows.size();
        int numCols = gridProps.columns.isEmpty() ? 1 : gridProps.columns.size();
        
        // Auto-expand grid if needed based on child count
        int childCount = children.size();
        if (gridProps.direction.isRow()) {
            // Row layout: expand columns as needed
            if (gridProps.overflow == GridLayoutProperties.Overflow.WRAP) {
                numCols = Math.max(numCols, childCount);
            }
            if (numRows == 0) numRows = 1;
        } else {
            // Column layout: expand rows as needed
            if (gridProps.overflow == GridLayoutProperties.Overflow.WRAP) {
                numRows = Math.max(numRows, childCount);
            }
            if (numCols == 0) numCols = 1;
        }
        
        // Measure children to determine content sizes
        List<LayoutEngine.MeasuredSize> measuredChildren = measureChildren(
            children, availableWidth, availableHeight
        );
        
        // Compute track sizes
        result.rows = computeTracks(
            gridProps.rows,
            numRows,
            availableHeight,
            gridProps.gap.getRowGap(),
            measuredChildren,
            childCount,
            numCols,
            true,  // isRow
            gridProps.resizableRows
        );
        
        result.columns = computeTracks(
            gridProps.columns,
            numCols,
            availableWidth,
            gridProps.gap.getColumnGap(),
            measuredChildren,
            childCount,
            numCols,
            false, // isColumn
            gridProps.resizableColumns
        );
        
        // Calculate total dimensions
        result.totalWidth = calculateTotalSize(result.columns, gridProps.gap.getColumnGap());
        result.totalHeight = calculateTotalSize(result.rows, gridProps.gap.getRowGap());
        
        // Position children in grid
        positionChildren(
            result,
            children,
            measuredChildren,
            gridProps,
            padding
        );
        
        return result;
    }
    
    /**
     * Measure all children
     */
    private static List<LayoutEngine.MeasuredSize> measureChildren(
        NoteBytesArray children,
        int availableWidth,
        int availableHeight
    ) {
        List<LayoutEngine.MeasuredSize> measured = new ArrayList<>();
        LayoutEngine.Constraints constraints = new LayoutEngine.Constraints(
            availableWidth,
            availableHeight
        );
        
        for (int i = 0; i < children.size(); i++) {
            NoteBytes item = children.get(i);
            if (!(item instanceof NoteBytesObject)) {
                measured.add(new LayoutEngine.MeasuredSize(0, 0));
                continue;
            }
            
            LayoutSegment child = new LayoutSegment((NoteBytesObject) item);
            LayoutEngine.MeasuredSize size = measureSegment(child, constraints);
            measured.add(size);
        }
        
        return measured;
    }
    
    private static LayoutEngine.MeasuredSize measureSegment(
        LayoutSegment segment,
        LayoutEngine.Constraints constraints
    ) {
        switch (segment.getType()) {
            case TEXT:
                return measureText(segment, constraints);
            case IMAGE:
                return LayoutEngine.measureImage(segment, constraints);
            case CONTAINER:
                return measureContainer(segment, constraints);
            default:
                return new LayoutEngine.MeasuredSize(0, 0);
        }
    }
    
    private static LayoutEngine.MeasuredSize measureText(
        LayoutSegment segment,
        LayoutEngine.Constraints constraints
    ) {
        // Simplified text measurement - use actual TextRenderer in production
        return new LayoutEngine.MeasuredSize(100, 20);
    }
    
    private static LayoutEngine.MeasuredSize measureContainer(
        LayoutSegment segment,
        LayoutEngine.Constraints constraints
    ) {
        Insets padding = segment.getLayout().padding;
        return new LayoutEngine.MeasuredSize(
            padding.left + padding.right + 100,
            padding.top + padding.bottom + 50
        );
    }
    
    /**
     * Compute track sizes (rows or columns)
     */
    private static List<ComputedTrack> computeTracks(
        List<GridLayoutProperties.TrackSize> trackDefs,
        int numTracks,
        int availableSpace,
        int gap,
        List<LayoutEngine.MeasuredSize> measuredChildren,
        int childCount,
        int numCols,
        boolean isRow,
        boolean resizable
    ) {
        List<ComputedTrack> tracks = new ArrayList<>();
        
        // Create tracks - either from definitions or auto-sized
        for (int i = 0; i < numTracks; i++) {
            tracks.add(new ComputedTrack(0, 0));
        }
        
        // Calculate content sizes for each track
        for (int i = 0; i < childCount; i++) {
            int trackIndex = isRow ? (i / numCols) : (i % numCols);
            if (trackIndex >= tracks.size()) break;
            
            LayoutEngine.MeasuredSize measured = measuredChildren.get(i);
            int contentSize = isRow ? measured.height : measured.width;
            
            ComputedTrack track = tracks.get(trackIndex);
            track.contentSize = Math.max(track.contentSize, contentSize);
        }
        
        // Calculate total gap space
        int totalGap = gap * (numTracks - 1);
        int remainingSpace = availableSpace - totalGap;
        
        // Phase 1: Resolve fixed and percentage sizes
        double totalFr = 0;
        for (int i = 0; i < tracks.size(); i++) {
            // Get track definition or default to auto
            GridLayoutProperties.TrackSize def = (trackDefs.isEmpty() || i >= trackDefs.size()) ? 
                GridLayoutProperties.TrackSize.auto() : trackDefs.get(i);
            ComputedTrack track = tracks.get(i);
            
            switch (def.getUnit()) {
                case PX:
                    track.size = def.resolve(availableSpace, remainingSpace, track.contentSize);
                    remainingSpace -= track.size;
                    break;
                    
                case PERCENT:
                    track.size = def.resolve(availableSpace, remainingSpace, track.contentSize);
                    remainingSpace -= track.size;
                    break;
                    
                case FR:
                    totalFr += def.getValue();
                    break;
                    
                case AUTO:
                case MIN_CONTENT:
                case MAX_CONTENT:
                    track.size = track.contentSize;
                    remainingSpace -= track.size;
                    break;
            }
            
            track.isResizable = resizable;
        }
        
        // Phase 2: Distribute remaining space to fractional tracks
        if (totalFr > 0 && remainingSpace > 0) {
            double frUnit = remainingSpace / totalFr;
            
            for (int i = 0; i < tracks.size(); i++) {
                // Get track definition or default to auto
                GridLayoutProperties.TrackSize def = (trackDefs.isEmpty() || i >= trackDefs.size()) ? 
                    GridLayoutProperties.TrackSize.auto() : trackDefs.get(i);
                ComputedTrack track = tracks.get(i);
                
                if (def.getUnit() == GridLayoutProperties.TrackSize.Unit.FR) {
                    track.size = (int) (def.getValue() * frUnit);
                    
                    // Apply constraints
                    if (def.getMinSize() > 0) {
                        track.size = Math.max(track.size, (int) def.getMinSize());
                    }
                    if (def.getMaxSize() < Double.MAX_VALUE) {
                        track.size = Math.min(track.size, (int) def.getMaxSize());
                    }
                }
            }
        }
        
        // Phase 3: Calculate start positions
        int currentPos = 0;
        for (ComputedTrack track : tracks) {
            track.start = currentPos;
            currentPos += track.size + gap;
        }
        
        return tracks;
    }
    
    /**
     * Calculate total size including gaps
     */
    private static int calculateTotalSize(List<ComputedTrack> tracks, int gap) {
        if (tracks.isEmpty()) return 0;
        
        ComputedTrack last = tracks.get(tracks.size() - 1);
        return last.getEnd();
    }
    
    /**
     * Position children in grid cells
     */
    private static void positionChildren(
        GridLayoutResult result,
        NoteBytesArray children,
        List<LayoutEngine.MeasuredSize> measured,
        GridLayoutProperties gridProps,
        Insets padding
    ) {
        int numCols = result.columns.size();
        int numRows = result.rows.size();
        
        for (int i = 0; i < children.size(); i++) {
            NoteBytes item = children.get(i);
            if (!(item instanceof NoteBytesObject)) continue;
            
            // Determine cell position
            int row, col;
            if (gridProps.direction.isRow()) {
                row = i / numCols;
                col = i % numCols;
            } else {
                row = i % numRows;
                col = i / numRows;
            }
            
            if (row >= numRows || col >= numCols) {
                // Overflow - handle based on overflow policy
                if (gridProps.overflow == GridLayoutProperties.Overflow.HIDDEN) {
                    continue;
                }
                // For now, skip
                continue;
            }
            
            ComputedTrack rowTrack = result.rows.get(row);
            ComputedTrack colTrack = result.columns.get(col);
            
            // Calculate cell bounds
            int cellX = colTrack.start + padding.left;
            int cellY = rowTrack.start + padding.top;
            int cellWidth = colTrack.size;
            int cellHeight = rowTrack.size;
            
            // Apply alignment within cell
            LayoutEngine.MeasuredSize childSize = measured.get(i);
            Rectangle childBounds = alignInCell(
                cellX, cellY, cellWidth, cellHeight,
                childSize.width, childSize.height,
                gridProps.justifyContent,
                gridProps.alignItems
            );
            
            result.cellBounds.add(childBounds);
        }
    }
    
    /**
     * Align child within cell
     */
    private static Rectangle alignInCell(
        int cellX, int cellY, int cellWidth, int cellHeight,
        int childWidth, int childHeight,
        GridLayoutProperties.JustifyContent justify,
        GridLayoutProperties.AlignItems align
    ) {
        Rectangle bounds = new Rectangle();
        
        // Horizontal alignment (justify)
        switch (justify) {
            case START:
                bounds.x = cellX;
                bounds.width = Math.min(childWidth, cellWidth);
                break;
            case END:
                bounds.x = cellX + cellWidth - childWidth;
                bounds.width = Math.min(childWidth, cellWidth);
                break;
            case CENTER:
                bounds.x = cellX + (cellWidth - childWidth) / 2;
                bounds.width = Math.min(childWidth, cellWidth);
                break;
            case SPACE_BETWEEN:
            case SPACE_AROUND:
            case SPACE_EVENLY:
                // These apply to multiple children - for single child, use START
                bounds.x = cellX;
                bounds.width = Math.min(childWidth, cellWidth);
                break;
        }
        
        // Vertical alignment (align)
        switch (align) {
            case START:
                bounds.y = cellY;
                bounds.height = Math.min(childHeight, cellHeight);
                break;
            case END:
                bounds.y = cellY + cellHeight - childHeight;
                bounds.height = Math.min(childHeight, cellHeight);
                break;
            case CENTER:
                bounds.y = cellY + (cellHeight - childHeight) / 2;
                bounds.height = Math.min(childHeight, cellHeight);
                break;
            case STRETCH:
                bounds.y = cellY;
                bounds.height = cellHeight;
                break;
            case BASELINE:
                // For now, treat as START
                bounds.y = cellY;
                bounds.height = Math.min(childHeight, cellHeight);
                break;
        }
        
        return bounds;
    }
}