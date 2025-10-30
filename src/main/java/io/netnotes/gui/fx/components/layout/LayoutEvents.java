package io.netnotes.gui.fx.components.layout;

public class LayoutEvents {
        
    public static class CursorMoveEvent {
        public final LayoutSegment segment;
        public final int localOffset;
        public final int globalOffset;
        
        public CursorMoveEvent(LayoutSegment segment, int localOffset, int globalOffset) {
            this.segment = segment;
            this.localOffset = localOffset;
            this.globalOffset = globalOffset;
        }
    }

    public static class TextChangeEvent {
        public final LayoutSegment segment;
        public final String oldText;
        public final String newText;
        public final int changeOffset;
        public final int changeLength;
        
        public TextChangeEvent(LayoutSegment segment, String oldText, String newText, 
                            int changeOffset, int changeLength) {
            this.segment = segment;
            this.oldText = oldText;
            this.newText = newText;
            this.changeOffset = changeOffset;
            this.changeLength = changeLength;
        }
    }

    public static class SegmentClickEvent {
        public final LayoutSegment segment;
        public final int x;
        public final int y;
        public final int clickCount;
        
        public SegmentClickEvent(LayoutSegment segment, int x, int y, int clickCount) {
            this.segment = segment;
            this.x = x;
            this.y = y;
            this.clickCount = clickCount;
        }
    }

    public static class SelectionChangeEvent {
        public final CursorSelectionSystem.Selection selection;
        public final int startOffset;
        public final int endOffset;
        
        public SelectionChangeEvent(CursorSelectionSystem.Selection selection, 
                                int startOffset, int endOffset) {
            this.selection = selection;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    public static class LayoutCompleteEvent {
        public final int totalWidth;
        public final int totalHeight;
        public final long computeTimeMs;
        
        public LayoutCompleteEvent(int totalWidth, int totalHeight, long computeTimeMs) {
            this.totalWidth = totalWidth;
            this.totalHeight = totalHeight;
            this.computeTimeMs = computeTimeMs;
        }
    }
}
