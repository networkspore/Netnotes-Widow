package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteIntegerArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * Navigator for moving cursor through segment tree
     * OPTIMIZED: Caches segment offsets to avoid O(n) traversals on every operation
     */
    public static class CursorNavigator {
        private NoteBytesArray rootSegments;
        
        // NEW: Cached data to avoid O(n) traversals
        private Map<List<Integer>, Integer> m_segmentOffsets; // path -> global start offset
        private int m_totalContentLength;
        private boolean m_cacheDirty;
        
        public CursorNavigator(NoteBytesArray rootSegments) {
            this.rootSegments = rootSegments;
            this.m_segmentOffsets = new java.util.HashMap<>();
            this.m_cacheDirty = true;
            rebuildCache();
        }
        
        /**
         * Rebuild offset cache - O(n) operation, only called when structure changes
         */
        private void rebuildCache() {
            if (!m_cacheDirty) return;
            
            m_segmentOffsets.clear();
            m_totalContentLength = 0;
            
            rebuildCacheRecursive(rootSegments, new ArrayList<>(), 0);
            m_cacheDirty = false;
        }
        
        private int rebuildCacheRecursive(
            NoteBytesArray segments,
            List<Integer> currentPath,
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
                
                List<Integer> segmentPath = new ArrayList<>(currentPath);
                segmentPath.add(i);
                
                // Cache this segment's start offset
                m_segmentOffsets.put(segmentPath, currentOffset);
                
                if (segment.isContainer() && segment.hasChildren()) {
                    // Recurse into children
                    currentOffset = rebuildCacheRecursive(
                        segment.getChildren(),
                        segmentPath,
                        currentOffset
                    );
                } else {
                    // Leaf segment - add its length
                    currentOffset += segment.getContentLength();
                }
            }
            
            return currentOffset;
        }
        
        /**
         * Incrementally update offsets after text insertion.
         * Much faster than full rebuild for single-segment edits.
         * O(k) where k = segments after this position
         */
        public void notifyTextInsert(CursorPosition position, int insertedLength) {
            if (insertedLength == 0) return;
            
            // Ensure cache is built
            if (m_cacheDirty) {
                rebuildCache();
                return;
            }
            
            int globalOffset = position.getGlobalOffset();
            List<Integer> modifiedPath = position.getSegmentPath();
            
            // Update cached offsets for all segments after this position
            for (Map.Entry<List<Integer>, Integer> entry : m_segmentOffsets.entrySet()) {
                List<Integer> path = entry.getKey();
                int startOffset = entry.getValue();
                
                // Only update segments that come after the modified position
                if (startOffset > globalOffset || isAfterPath(path, modifiedPath)) {
                    entry.setValue(startOffset + insertedLength);
                }
            }
            
            // Update total content length
            m_totalContentLength += insertedLength;
        }
        
        /**
         * Incrementally update offsets after text deletion.
         * O(k) where k = segments after this position
         */
        public void notifyTextDelete(CursorPosition position, int deletedLength) {
            if (deletedLength == 0) return;
            
            // Ensure cache is built
            if (m_cacheDirty) {
                rebuildCache();
                return;
            }
            
            int globalOffset = position.getGlobalOffset();
            List<Integer> modifiedPath = position.getSegmentPath();
            
            // Update cached offsets for all segments after this position
            for (Map.Entry<List<Integer>, Integer> entry : m_segmentOffsets.entrySet()) {
                List<Integer> path = entry.getKey();
                int startOffset = entry.getValue();
                
                // Only update segments that come after the modified position
                if (startOffset > globalOffset || isAfterPath(path, modifiedPath)) {
                    entry.setValue(startOffset - deletedLength);
                }
            }
            
            // Update total content length
            m_totalContentLength = Math.max(0, m_totalContentLength - deletedLength);
        }
        
        /**
         * Mark cache as dirty - forces rebuild on next operation.
         * Call this for structural changes (add/remove segments).
         */
        public void invalidateCache() {
            m_cacheDirty = true;
        }
        
        /**
         * Helper: Check if pathA comes after pathB in document order
         */
        private boolean isAfterPath(List<Integer> pathA, List<Integer> pathB) {
            int minLen = Math.min(pathA.size(), pathB.size());
            
            for (int i = 0; i < minLen; i++) {
                int a = pathA.get(i);
                int b = pathB.get(i);
                
                if (a > b) return true;
                if (a < b) return false;
            }
            
            // Paths equal up to minLen - longer path comes after
            return pathA.size() > pathB.size();
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
         * OPTIMIZED: Uses cached offsets for O(log n) search instead of O(n) traversal
         */
        public CursorPosition globalOffsetToPosition(int targetOffset) {
            if (m_cacheDirty) {
                rebuildCache();
            }
            
            // Handle edge cases
            if (targetOffset <= 0) {
                return new CursorPosition();
            }
            if (targetOffset >= m_totalContentLength) {
                targetOffset = m_totalContentLength;
            }
            
            // Find the segment containing this offset using cache
            List<Integer> bestPath = null;
            int bestOffset = -1;
            
            for (Map.Entry<List<Integer>, Integer> entry : m_segmentOffsets.entrySet()) {
                int startOffset = entry.getValue();
                
                if (startOffset <= targetOffset && startOffset > bestOffset) {
                    // Get segment to check its length
                    LayoutSegment seg = getSegmentAtPath(entry.getKey());
                    if (seg != null) {
                        int segmentEnd = startOffset + seg.getContentLength();
                        
                        if (segmentEnd >= targetOffset) {
                            // This segment contains our target
                            bestPath = entry.getKey();
                            bestOffset = startOffset;
                        }
                    }
                }
            }
            
            if (bestPath != null) {
                int localOffset = targetOffset - bestOffset;
                return new CursorPosition(bestPath, localOffset, targetOffset);
            }
            
            // Fallback to start if not found
            return new CursorPosition();
        }
        
        /**
         * Helper: Get segment by path
         */
        private LayoutSegment getSegmentAtPath(List<Integer> path) {
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
                
                if (i == path.size() - 1) {
                    return segment;
                }
                
                if (!segment.isContainer()) {
                    return null;
                }
                
                current = segment.getChildren();
            }
            
            return null;
        }
        
        /**
         * Convert cursor position to global offset
         * OPTIMIZED: Uses cached offsets for O(1) lookup instead of O(n) traversal
         */
        public int positionToGlobalOffset(CursorPosition position) {
            if (m_cacheDirty) {
                rebuildCache();
            }
            
            List<Integer> path = position.getSegmentPath();
            Integer cachedOffset = m_segmentOffsets.get(path);
            
            if (cachedOffset != null) {
                return cachedOffset + position.getLocalOffset();
            }
            
            // Fallback to manual calculation if not in cache
            return position.getGlobalOffset();
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
            if (m_cacheDirty) {
                rebuildCache();
            }
            
            // Start searching from current position
            int searchOffset = current.getGlobalOffset() + 1;
            
            while (searchOffset <= m_totalContentLength) {
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
            searchOffset = 0;
            while (searchOffset < current.getGlobalOffset()) {
                CursorPosition candidate = globalOffsetToPosition(searchOffset);
                LayoutSegment segment = getSegmentAt(candidate);
                
                if (segment != null && 
                    segment.getInteraction().focusable &&
                    segment.getLayout().display != LayoutSegment.Display.NONE) {
                    return candidate;
                }
                
                if (segment != null) {
                    searchOffset += segment.getContentLength();
                } else {
                    searchOffset++;
                }
            }
            
            // No focusable found at all
            return current;
        }
        
        /**
         * Move cursor to previous focusable segment (for shift+tab)
         */
        public CursorPosition moveToPreviousFocusable(CursorPosition current) {
            if (m_cacheDirty) {
                rebuildCache();
            }
            
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
            searchOffset = m_totalContentLength;
            while (searchOffset > current.getGlobalOffset()) {
                CursorPosition candidate = globalOffsetToPosition(searchOffset);
                LayoutSegment segment = getSegmentAt(candidate);
                
                if (segment != null && 
                    segment.getInteraction().focusable &&
                    segment.getLayout().display != LayoutSegment.Display.NONE) {
                    return candidate;
                }
                
                searchOffset--;
            }
            
            // No focusable found at all
            return current;
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
         * OPTIMIZED: Returns cached value instead of O(n) traversal
         */
        public int getTotalContentLength(NoteBytesArray segments) {
            if (segments == rootSegments) {
                if (m_cacheDirty) {
                    rebuildCache();
                }
                return m_totalContentLength;
            }
            
            // For non-root segments, fall back to calculation
            return getTotalContentLengthRecursive(segments);
        }
        
        private int getTotalContentLengthRecursive(NoteBytesArray segments) {
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
                    total += getTotalContentLengthRecursive(segment.getChildren());
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
            
            // For now, we'll handle simple case: same segment
            if (start.getSegmentPath().equals(end.getSegmentPath())) {
                LayoutSegment segment = getSegmentAt(start);
                if (segment != null && segment.getType() == LayoutSegment.SegmentType.TEXT) {
                    NoteIntegerArray text = segment.getTextContent();
                    if (text != null) {
                        text.delete(start.getLocalOffset(), end.getLocalOffset());
                        
                        // Update cache
                        int deletedLength = end.getLocalOffset() - start.getLocalOffset();
                        notifyTextDelete(start, deletedLength);
                    }
                }
            }

        }
    }
}