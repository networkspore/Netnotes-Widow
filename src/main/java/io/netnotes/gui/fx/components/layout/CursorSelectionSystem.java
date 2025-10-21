package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteIntegerArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursor and Selection system for traversing and selecting content in a segment tree.
 * 
 * Key Concepts:
 * 1. Global Offset - Absolute position in flattened document (like DOM offset)
 * 2. Segment Path - Hierarchical path to reach a segment (for nested containers)
 * 3. Local Offset - Position within a specific segment's content
 * 4. Traversal - Moving cursor respecting focusable/editable/hidden properties
 * 5. Selection - Range between two cursor positions
 */
public class CursorSelectionSystem {
    
    /**
     * Represents a position in the segment tree.
     * Can be converted between global offset and segment path.
     */
    public static class CursorPosition {
        // Hierarchical path to segment (indices into nested children arrays)
        // Example: [0, 2, 1] means root.children[0].children[2].children[1]
        private List<Integer> segmentPath;
        
        // Offset within the segment's content
        // For TEXT: code point index
        // For IMAGE/COMPONENT: 0 or 1 (before/after)
        // For CONTAINER: index of child segment
        private int localOffset;
        
        // Cached global offset (updated when cursor moves)
        private int globalOffset;
        
        public CursorPosition() {
            this.segmentPath = new ArrayList<>();
            this.segmentPath.add(0); // Start at root
            this.localOffset = 0;
            this.globalOffset = 0;
        }
        
        public CursorPosition(List<Integer> path, int offset, int global) {
            this.segmentPath = new ArrayList<>(path);
            this.localOffset = offset;
            this.globalOffset = global;
        }
        
        public List<Integer> getSegmentPath() {
            return new ArrayList<>(segmentPath);
        }
        
        public int getLocalOffset() {
            return localOffset;
        }
        
        public int getGlobalOffset() {
            return globalOffset;
        }
        
        public void setSegmentPath(List<Integer> path) {
            this.segmentPath = new ArrayList<>(path);
        }
        
        public void setLocalOffset(int offset) {
            this.localOffset = offset;
        }
        
        public void setGlobalOffset(int global) {
            this.globalOffset = global;
        }
        
        public CursorPosition copy() {
            return new CursorPosition(segmentPath, localOffset, globalOffset);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CursorPosition)) return false;
            CursorPosition other = (CursorPosition) obj;
            return segmentPath.equals(other.segmentPath) && 
                   localOffset == other.localOffset;
        }
        
        @Override
        public int hashCode() {
            return segmentPath.hashCode() * 31 + localOffset;
        }
        
        @Override
        public String toString() {
            return String.format("CursorPosition[path=%s, local=%d, global=%d]", 
                segmentPath, localOffset, globalOffset);
        }
    }
    
    /**
     * Represents a selection range in the document
     */
    public static class Selection {
        private CursorPosition start;
        private CursorPosition end;
        
        public Selection(CursorPosition start, CursorPosition end) {
            this.start = start.copy();
            this.end = end.copy();
        }
        
        public CursorPosition getStart() {
            return start;
        }
        
        public CursorPosition getEnd() {
            return end;
        }
        
        public boolean isEmpty() {
            return start.equals(end);
        }
        
        public boolean contains(CursorPosition pos) {
            int startGlobal = start.getGlobalOffset();
            int endGlobal = end.getGlobalOffset();
            int posGlobal = pos.getGlobalOffset();
            
            int min = Math.min(startGlobal, endGlobal);
            int max = Math.max(startGlobal, endGlobal);
            
            return posGlobal >= min && posGlobal <= max;
        }
        
        public Selection normalized() {
            if (start.getGlobalOffset() <= end.getGlobalOffset()) {
                return new Selection(start, end);
            } else {
                return new Selection(end, start);
            }
        }
        
        @Override
        public String toString() {
            return String.format("Selection[%d-%d]", 
                start.getGlobalOffset(), end.getGlobalOffset());
        }
    }
    
    /**
     * Context for traversing the segment tree
     */
    private static class TraversalContext {
        int globalOffset = 0;
        boolean found = false;
        CursorPosition result = null;
    }
    
    /**
     * Navigator for moving cursor through segment tree
     */
    public static class CursorNavigator {
        private NoteBytesArray rootSegments;
        
        public CursorNavigator(NoteBytesArray rootSegments) {
            this.rootSegments = rootSegments;
        }
        
        /**
         * Get segment at the given cursor position
         */
        public LayoutSegment getSegmentAt(CursorPosition position) {
            List<Integer> path = position.getSegmentPath();
            NoteBytesArray current = rootSegments;
            
            for (int i = 0; i < path.size(); i++) {
                int index = path.get(i);
                
                if (index < 0 || index >= current.size()) {
                    return null;
                }
                
                NoteBytes item = current.get(index);
                if (!(item instanceof NoteBytesObject)) {
                    return null;
                }
                
                LayoutSegment segment = new LayoutSegment((NoteBytesObject) item);
                
                // Last element in path - this is our segment
                if (i == path.size() - 1) {
                    return segment;
                }
                
                // Not last - traverse into children
                if (!segment.isContainer()) {
                    return null; // Path goes deeper but segment has no children
                }
                
                current = segment.getChildren();
            }
            
            return null;
        }
        
        /**
         * Convert global offset to cursor position
         */
        public CursorPosition globalOffsetToPosition(int targetOffset) {
            TraversalContext ctx = new TraversalContext();
            List<Integer> path = new ArrayList<>();
            
            globalOffsetToPositionRecursive(rootSegments, targetOffset, path, ctx);
            
            return ctx.result != null ? ctx.result : new CursorPosition();
        }
        
        private void globalOffsetToPositionRecursive(
            NoteBytesArray segments, 
            int targetOffset,
            List<Integer> currentPath,
            TraversalContext ctx
        ) {
            if (ctx.found) return;
            
            for (int i = 0; i < segments.size(); i++) {
                if (ctx.found) break;
                
                NoteBytes item = segments.get(i);
                if (!(item instanceof NoteBytesObject)) continue;
                
                LayoutSegment segment = new LayoutSegment((NoteBytesObject) item);
                
                // Skip display:none (doesn't exist in layout)
                if (segment.getLayout().display == LayoutSegment.Display.NONE) {
                    continue;
                }
                
                List<Integer> segmentPath = new ArrayList<>(currentPath);
                segmentPath.add(i);
                
                if (segment.isContainer() && segment.hasChildren()) {
                    // Container - recurse into children
                    globalOffsetToPositionRecursive(
                        segment.getChildren(), 
                        targetOffset, 
                        segmentPath, 
                        ctx
                    );
                } else {
                    // Leaf segment - check if target is within
                    int contentLength = segment.getContentLength();
                    
                    if (ctx.globalOffset + contentLength >= targetOffset) {
                        // Found it!
                        int localOffset = targetOffset - ctx.globalOffset;
                        ctx.result = new CursorPosition(
                            segmentPath, 
                            localOffset, 
                            targetOffset
                        );
                        ctx.found = true;
                        return;
                    }
                    
                    ctx.globalOffset += contentLength;
                }
            }
        }
        
        /**
         * Convert cursor position to global offset
         */
        public int positionToGlobalOffset(CursorPosition position) {
            TraversalContext ctx = new TraversalContext();
            List<Integer> targetPath = position.getSegmentPath();
            
            positionToGlobalOffsetRecursive(rootSegments, targetPath, 0, ctx);
            
            return ctx.globalOffset + position.getLocalOffset();
        }
        
        private void positionToGlobalOffsetRecursive(
            NoteBytesArray segments,
            List<Integer> targetPath,
            int pathDepth,
            TraversalContext ctx
        ) {
            if (ctx.found) return;
            
            if (pathDepth >= targetPath.size()) {
                ctx.found = true;
                return;
            }
            
            int targetIndex = targetPath.get(pathDepth);
            
            for (int i = 0; i < segments.size(); i++) {
                if (ctx.found) break;
                
                NoteBytes item = segments.get(i);
                if (!(item instanceof NoteBytesObject)) continue;
                
                LayoutSegment segment = new LayoutSegment((NoteBytesObject) item);
                
                // Skip display:none
                if (segment.getLayout().display == LayoutSegment.Display.NONE) {
                    continue;
                }
                
                if (i == targetIndex) {
                    // This is part of our path
                    if (pathDepth == targetPath.size() - 1) {
                        // Found our segment
                        ctx.found = true;
                        return;
                    } else {
                        // Keep traversing
                        if (segment.isContainer() && segment.hasChildren()) {
                            positionToGlobalOffsetRecursive(
                                segment.getChildren(),
                                targetPath,
                                pathDepth + 1,
                                ctx
                            );
                        }
                        return;
                    }
                } else {
                    // Not our path - add this segment's length
                    if (segment.isContainer() && segment.hasChildren()) {
                        ctx.globalOffset += getTotalContentLength(segment.getChildren());
                    } else {
                        ctx.globalOffset += segment.getContentLength();
                    }
                }
            }
        }
        
        /**
         * Move cursor forward by one position
         */
        public CursorPosition moveForward(CursorPosition current) {
            LayoutSegment segment = getSegmentAt(current);
            if (segment == null) return current;
            
            int contentLength = segment.getContentLength();
            
            // Can we move within current segment?
            if (current.getLocalOffset() < contentLength) {
                CursorPosition next = current.copy();
                next.setLocalOffset(current.getLocalOffset() + 1);
                next.setGlobalOffset(current.getGlobalOffset() + 1);
                return next;
            }
            
            // Move to next segment
            int nextGlobal = current.getGlobalOffset() + 1;
            return globalOffsetToPosition(nextGlobal);
        }
        
        /**
         * Move cursor backward by one position
         */
        public CursorPosition moveBackward(CursorPosition current) {
            if (current.getGlobalOffset() <= 0) {
                return current; // Already at start
            }
            
            if (current.getLocalOffset() > 0) {
                // Move within current segment
                CursorPosition prev = current.copy();
                prev.setLocalOffset(current.getLocalOffset() - 1);
                prev.setGlobalOffset(current.getGlobalOffset() - 1);
                return prev;
            }
            
            // Move to previous segment
            int prevGlobal = current.getGlobalOffset() - 1;
            return globalOffsetToPosition(prevGlobal);
        }
        
        /**
         * Move cursor to next focusable segment (for tab navigation)
         */
        public CursorPosition moveToNextFocusable(CursorPosition current) {
            // Start searching from current position
            int searchOffset = current.getGlobalOffset() + 1;
            int totalLength = getTotalContentLength(rootSegments);
            
            while (searchOffset <= totalLength) {
                CursorPosition candidate = globalOffsetToPosition(searchOffset);
                LayoutSegment segment = getSegmentAt(candidate);
                
                if (segment != null && 
                    segment.getInteraction().focusable &&
                    segment.getLayout().display != LayoutSegment.Display.NONE) {
                    return candidate;
                }
                
                // Skip this segment entirely
                if (segment != null) {
                    searchOffset += segment.getContentLength();
                } else {
                    searchOffset++;
                }
            }
            
            // No focusable found - wrap to beginning
            return moveToNextFocusable(new CursorPosition());
        }
        
        /**
         * Move cursor to previous focusable segment (for shift+tab)
         */
        public CursorPosition moveToPreviousFocusable(CursorPosition current) {
            int searchOffset = current.getGlobalOffset() - 1;
            
            while (searchOffset >= 0) {
                CursorPosition candidate = globalOffsetToPosition(searchOffset);
                LayoutSegment segment = getSegmentAt(candidate);
                
                if (segment != null && 
                    segment.getInteraction().focusable &&
                    segment.getLayout().display != LayoutSegment.Display.NONE) {
                    return candidate;
                }
                
                searchOffset--;
            }
            
            // No focusable found - wrap to end
            int totalLength = getTotalContentLength(rootSegments);
            return moveToPreviousFocusable(globalOffsetToPosition(totalLength));
        }
        
        /**
         * Check if cursor can edit at this position
         */
        public boolean canEditAt(CursorPosition position) {
            LayoutSegment segment = getSegmentAt(position);
            if (segment == null) return false;
            
            return segment.getInteraction().editable && 
                   segment.getType() == LayoutSegment.SegmentType.TEXT;
        }
        
        /**
         * Check if cursor can select at this position
         */
        public boolean canSelectAt(CursorPosition position) {
            LayoutSegment segment = getSegmentAt(position);
            if (segment == null) return false;
            
            return segment.getInteraction().selectable &&
                   segment.getLayout().display != LayoutSegment.Display.NONE;
        }
        
        /**
         * Get total content length of all segments
         */
        public int getTotalContentLength(NoteBytesArray segments) {
            int total = 0;
            
            for (int i = 0; i < segments.size(); i++) {
                NoteBytes item = segments.get(i);
                if (!(item instanceof NoteBytesObject)) continue;
                
                LayoutSegment segment = new LayoutSegment((NoteBytesObject) item);
                
                // Skip display:none
                if (segment.getLayout().display == LayoutSegment.Display.NONE) {
                    continue;
                }
                
                if (segment.isContainer() && segment.hasChildren()) {
                    total += getTotalContentLength(segment.getChildren());
                } else {
                    total += segment.getContentLength();
                }
            }
            
            return total;
        }
        
        /**
         * Get all text content in selection range
         */
        public String getTextInRange(Selection selection) {
            Selection normalized = selection.normalized();
            int startGlobal = normalized.getStart().getGlobalOffset();
            int endGlobal = normalized.getEnd().getGlobalOffset();
            
            StringBuilder result = new StringBuilder();
            getTextInRangeRecursive(rootSegments, startGlobal, endGlobal, result, 0);
            
            return result.toString();
        }
        
        private int getTextInRangeRecursive(
            NoteBytesArray segments,
            int startGlobal,
            int endGlobal,
            StringBuilder result,
            int currentOffset
        ) {
            for (int i = 0; i < segments.size(); i++) {
                NoteBytes item = segments.get(i);
                if (!(item instanceof NoteBytesObject)) continue;
                
                LayoutSegment segment = new LayoutSegment((NoteBytesObject) item);
                
                // Skip display:none
                if (segment.getLayout().display == LayoutSegment.Display.NONE) {
                    continue;
                }
                
                if (segment.isContainer() && segment.hasChildren()) {
                    currentOffset = getTextInRangeRecursive(
                        segment.getChildren(),
                        startGlobal,
                        endGlobal,
                        result,
                        currentOffset
                    );
                } else {
                    int contentLength = segment.getContentLength();
                    int segmentEnd = currentOffset + contentLength;
                    
                    // Check if this segment overlaps selection
                    if (segmentEnd > startGlobal && currentOffset < endGlobal) {
                        // Calculate overlap
                        int extractStart = Math.max(0, startGlobal - currentOffset);
                        int extractEnd = Math.min(contentLength, endGlobal - currentOffset);
                        
                        if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
                            NoteIntegerArray text = segment.getTextContent();
                            if (text != null) {
                                String str = text.toString();
                                if (extractEnd <= str.length()) {
                                    result.append(str.substring(extractStart, extractEnd));
                                }
                            }
                        } else {
                            // Non-text content - represent as special character
                            result.append('\uFFFC'); // Object replacement character
                        }
                    }
                    
                    currentOffset = segmentEnd;
                }
                
                if (currentOffset >= endGlobal) {
                    break;
                }
            }
            
            return currentOffset;
        }
        
        /**
         * Delete content in selection range
         */
        public void deleteRange(Selection selection) {
            Selection normalized = selection.normalized();
            CursorPosition start = normalized.getStart();
            CursorPosition end = normalized.getEnd();
            
            // TODO: Implement deletion across segments
            // This is complex because it may need to:
            // 1. Delete partial content in start segment
            // 2. Delete entire intermediate segments
            // 3. Delete partial content in end segment
            // 4. Merge remaining content if in same container
            
            // For now, we'll handle simple case: same segment
            if (start.getSegmentPath().equals(end.getSegmentPath())) {
                LayoutSegment segment = getSegmentAt(start);
                if (segment != null && segment.getType() == LayoutSegment.SegmentType.TEXT) {
                    NoteIntegerArray text = segment.getTextContent();
                    if (text != null) {
                        text.delete(start.getLocalOffset(), end.getLocalOffset());
                    }
                }
            }
        }
    }
}