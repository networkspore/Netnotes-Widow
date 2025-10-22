package io.netnotes.gui.fx.components.fields;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.gui.fx.components.canvas.BufferedCanvasView;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.GraphicsContextPool;
import io.netnotes.gui.fx.display.TextRenderer;
import io.netnotes.gui.fx.utils.TaskUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-line text area with segment-based storage using NoteBytesArray.
 * Each segment is a NoteBytesObject containing content and formatting.
 * Text content is stored as NoteIntegerArray (code points).
 */
public class BufferedTextArea extends BufferedCanvasView {
    private static final int DEFAULT_WIDTH = 600;
    private static final int DEFAULT_HEIGHT = 400;
    private static final int VIEWPORT_BUFFER = GraphicsContextPool.SIZE_TOLERANCE;
    private static final int LINE_SPACING = 4; // Extra pixels between lines
    
    /**
     * Segment types for the text area
     */
    public enum SegmentType {
        TEXT(0),
        IMAGE(1),
        EMBEDDED(2);
        
        private final int value;
        
        SegmentType(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static SegmentType fromValue(int value) {
            for (SegmentType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return TEXT;
        }
    }
    
    /**
     * Represents a text segment with content and formatting
     */
    private static class TextSegment {
        NoteBytesObject data;
        SegmentType type;
        NoteIntegerArray content;
        Font font;
        Color textColor;
        boolean bold;
        boolean italic;
        
        // Cached measurements
        int startOffset; // Global code point offset
        int length; // Code point length
        
        TextSegment(NoteBytesObject data) {
            this.data = data;
            parseData();
        }
        
        TextSegment(String text, Font font, Color color) {
            this.type = SegmentType.TEXT;
            this.content = new NoteIntegerArray(text);
            this.font = font;
            this.textColor = color;
            this.bold = false;
            this.italic = false;
            this.length = content.length();
            rebuildData();
        }
        
        private void parseData() {
            NoteBytesPair typePair = data.get("type");
            this.type = typePair != null ? 
                SegmentType.fromValue(typePair.getValue().getAsInt()) : SegmentType.TEXT;
            
            NoteBytesPair contentPair = data.get("content");
            this.content = contentPair != null ? 
                new NoteIntegerArray(contentPair.getValue().get()) : new NoteIntegerArray();
            
            this.length = content.length();
            
            // Parse formatting
            NoteBytesPair formatPair = data.get("formatting");
            if (formatPair != null && formatPair.getValue() instanceof NoteBytesObject) {
                NoteBytesObject formatting = (NoteBytesObject) formatPair.getValue();
                parseFormatting(formatting);
            } else {
                // Default formatting
                this.font = new Font("Monospaced", Font.PLAIN, 14);
                this.textColor = Color.BLACK;
                this.bold = false;
                this.italic = false;
            }
        }
        
        private void parseFormatting(NoteBytesObject formatting) {
            NoteBytesPair fontNamePair = formatting.get("fontName");
            NoteBytesPair fontSizePair = formatting.get("fontSize");
            NoteBytesPair boldPair = formatting.get("bold");
            NoteBytesPair italicPair = formatting.get("italic");
            NoteBytesPair colorPair = formatting.get("color");
            
            String fontName = fontNamePair != null ? fontNamePair.getValue().getAsString() : "Monospaced";
            int fontSize = fontSizePair != null ? fontSizePair.getValue().getAsInt() : 14;
            this.bold = boldPair != null && boldPair.getValue().getAsBoolean();
            this.italic = italicPair != null && italicPair.getValue().getAsBoolean();
            
            int style = Font.PLAIN;
            if (bold && italic) style = Font.BOLD | Font.ITALIC;
            else if (bold) style = Font.BOLD;
            else if (italic) style = Font.ITALIC;
            
            this.font = new Font(fontName, style, fontSize);
            
            if (colorPair != null) {
                int argb = colorPair.getValue().getAsInt();
                this.textColor = new Color(argb, true);
            } else {
                this.textColor = Color.BLACK;
            }
        }
        
        private void rebuildData() {
            data = new NoteBytesObject();
            data.add("type", new NoteInteger(type.getValue()));
            data.add("content", content);
            
            // Build formatting object
            NoteBytesObject formatting = new NoteBytesObject();
            formatting.add("fontName", font.getName());
            formatting.add("fontSize", font.getSize());
            formatting.add("bold", bold);
            formatting.add("italic", italic);
            formatting.add("color", textColor.getRGB());
            
            data.add("formatting", formatting);
        }
        
        String getText() {
            return content.toString();
        }
        
        void setText(String text) {
            this.content = new NoteIntegerArray(text);
            this.length = content.length();
            rebuildData();
        }
        
        void insertAt(int offset, String text) {
            content.insert(offset, text);
            this.length = content.length();
            rebuildData();
        }
        
        void deleteRange(int start, int end) {
            content.delete(start, end);
            this.length = content.length();
            rebuildData();
        }
        
        TextSegment copy() {
            TextSegment copy = new TextSegment(this.data);
            copy.content = new NoteIntegerArray(this.content.get());
            copy.font = this.font;
            copy.textColor = this.textColor;
            copy.bold = this.bold;
            copy.italic = this.italic;
            copy.length = this.length;
            return copy;
        }
    }
    
    /**
     * Represents a line of text that may span multiple segments
     */
    private static class LineInfo {
        int startSegment;
        int startOffset; // Offset within start segment
        int endSegment;
        int endOffset; // Offset within end segment
        double y; // Y position for rendering
        double height; // Line height
        int globalStartOffset; // Global code point offset
        int globalEndOffset;
        
        LineInfo(int startSeg, int startOff, int endSeg, int endOff, double y, double height) {
            this.startSegment = startSeg;
            this.startOffset = startOff;
            this.endSegment = endSeg;
            this.endOffset = endOff;
            this.y = y;
            this.height = height;
        }
    }
    
    /**
     * Cursor position within the document
     */
    private static class CursorPosition {
        int segmentIndex;
        int offsetInSegment; // Code point offset within segment
        
        CursorPosition(int segment, int offset) {
            this.segmentIndex = segment;
            this.offsetInSegment = offset;
        }
        
        CursorPosition copy() {
            return new CursorPosition(segmentIndex, offsetInSegment);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CursorPosition)) return false;
            CursorPosition other = (CursorPosition) obj;
            return segmentIndex == other.segmentIndex && 
                   offsetInSegment == other.offsetInSegment;
        }
    }
    
    // Storage
    private NoteBytesArray m_segments;
    private List<TextSegment> m_segmentCache;
    
    // Cursor and selection
    private CursorPosition m_cursor;
    private CursorPosition m_selectionStart;
    private CursorPosition m_selectionEnd;
    private boolean m_isSelecting;
    
    // Layout
    private List<LineInfo> m_lineLayout;
    private boolean m_layoutDirty;
    private int m_scrollY;
    private int m_maxScrollY;
    
    // Visual state
    private boolean m_cursorVisible;
    private boolean m_isFocused;
    private Timeline m_cursorTimeline;
    
    // Styling
    private Font m_defaultFont;
    private Color m_defaultTextColor;
    private Color m_cursorColor;
    private Color m_selectionColor;
    private Color m_selectedTextColor;
    private Color m_backgroundColor;
    
    // Dimensions
    private int m_preferredWidth;
    private int m_preferredHeight;
    private int m_viewportWidth;
    private int m_viewportHeight;
    private Insets m_insets;
    
    // Text renderer
    private final TextRenderer m_textRenderer = TextRenderer.getInstance();
    
    // Editable state
    private boolean m_editable = true;
    
    // Layout listeners
    private ChangeListener<Number> widthListener;
    private ChangeListener<Number> heightListener;
    
    // ========== Constructors ==========
    
    public BufferedTextArea() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
    
    public BufferedTextArea(int width, int height) {
        super();
        
        m_preferredWidth = width;
        m_preferredHeight = height;
        m_viewportWidth = width + VIEWPORT_BUFFER;
        m_viewportHeight = height + VIEWPORT_BUFFER;
        
        m_segments = new NoteBytesArray();
        m_segmentCache = new ArrayList<>();
        m_lineLayout = new ArrayList<>();
        m_layoutDirty = true;
        
        m_cursor = new CursorPosition(0, 0);
        m_selectionStart = null;
        m_selectionEnd = null;
        
        m_scrollY = 0;
        m_maxScrollY = 0;
        
        m_defaultFont = new Font("Monospaced", Font.PLAIN, 14);
        m_defaultTextColor = Color.BLACK;
        m_cursorColor = new Color(59, 130, 246);
        m_selectionColor = new Color(59, 130, 246, 80);
        m_selectedTextColor = Color.WHITE;
        m_backgroundColor = Color.WHITE;
        
        m_insets = new Insets(10);
        
        setRenderMode(RenderMode.GENERATE);
        setFocusTraversable(true);
        
        // Initialize with empty text segment
        addSegment(new TextSegment("", m_defaultFont, m_defaultTextColor));
        
        setupCursorBlink();
        setupEventHandlers();
        setupLayoutListeners();
        
        requestRender();
    }
    
    // ========== BufferedCanvasView Implementation ==========
    
    @Override
    protected int getGeneratedWidth() {
        return m_viewportWidth;
    }
    
    @Override
    protected int getGeneratedHeight() {
        return m_viewportHeight;
    }
    
    @Override
    public double prefWidth(double height) {
        return m_preferredWidth;
    }
    
    @Override
    public double prefHeight(double width) {
        return m_preferredHeight;
    }
    
    private void setupLayoutListeners() {
        widthListener = (_, _, newVal) -> {
            int val = (int) Math.ceil(newVal.doubleValue());
            if (val > 0 && val != m_preferredWidth) {
                m_preferredWidth = val;
                m_viewportWidth = val + VIEWPORT_BUFFER;
                m_layoutDirty = true;
                requestRender();
            }
        };
        widthProperty().addListener(widthListener);
        
        heightListener = (_, _, newVal) -> {
            int val = (int) Math.ceil(newVal.doubleValue());
            if (val > 0 && val != m_preferredHeight) {
                m_preferredHeight = val;
                m_viewportHeight = val + VIEWPORT_BUFFER;
                requestRender();
            }
        };
        heightProperty().addListener(heightListener);
    }
    
    @Override
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Recompute layout if needed
        if (m_layoutDirty) {
            computeLayout();
            m_layoutDirty = false;
        }
        
        // Clear background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        g2d.setColor(m_backgroundColor);
        g2d.fillRect(0, 0, width, height);
        
        // Set clipping region
        int paddingLeft = (int) m_insets.getLeft();
        int paddingTop = (int) m_insets.getTop();
        int paddingRight = (int) m_insets.getRight();
        int paddingBottom = (int) m_insets.getBottom();
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        int availableHeight = m_preferredHeight - paddingTop - paddingBottom;
        
        g2d.setClip(paddingLeft, paddingTop, availableWidth, availableHeight);
        
        // Render visible lines
        renderVisibleLines(g2d, paddingLeft, paddingTop, availableWidth, availableHeight);
        
        // Render cursor
        if (m_isFocused && m_cursorVisible && !hasSelection()) {
            renderCursor(g2d, paddingLeft, paddingTop);
        }
        
        g2d.setClip(null);
    }
    
    // ========== Layout Computation ==========
    
    private void computeLayout() {
        m_lineLayout.clear();
        
        if (m_segmentCache.isEmpty()) {
            return;
        }
        
        int paddingLeft = (int) m_insets.getLeft();
        int paddingRight = (int) m_insets.getRight();
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        
        double currentY = m_insets.getTop();
        int globalOffset = 0;
        
        // Update segment start offsets
        for (TextSegment segment : m_segmentCache) {
            segment.startOffset = globalOffset;
            globalOffset += segment.length;
        }
        
        // Layout lines
        int segIdx = 0;
        int segOffset = 0;
        
        while (segIdx < m_segmentCache.size()) {
            TextSegment segment = m_segmentCache.get(segIdx);
            String text = segment.getText();
            
            if (text.isEmpty()) {
                // Empty segment - create empty line
                FontMetrics metrics = m_textRenderer.getMetrics(segment.font);
                double lineHeight = metrics.getHeight() + LINE_SPACING;
                
                LineInfo line = new LineInfo(segIdx, 0, segIdx, 0, currentY, lineHeight);
                line.globalStartOffset = segment.startOffset;
                line.globalEndOffset = segment.startOffset;
                m_lineLayout.add(line);
                
                currentY += lineHeight;
                segIdx++;
                segOffset = 0;
                continue;
            }
            
            // Find next newline or end of segment
            int newlineIdx = text.indexOf('\n', segOffset);
            int endIdx = newlineIdx != -1 ? newlineIdx : text.length();
            
            String lineText = text.substring(segOffset, endIdx);
            FontMetrics metrics = m_textRenderer.getMetrics(segment.font);
            
            // Word wrap if needed
            if (m_textRenderer.getTextWidth(lineText, segment.font) > availableWidth) {
                // Find last space that fits
                int wrapIdx = findWrapPoint(lineText, segment.font, availableWidth);
                if (wrapIdx > 0) {
                    endIdx = segOffset + wrapIdx;
                    lineText = text.substring(segOffset, endIdx);
                }
            }
            
            double lineHeight = metrics.getHeight() + LINE_SPACING;
            
            LineInfo line = new LineInfo(
                segIdx, segOffset,
                segIdx, endIdx,
                currentY, lineHeight
            );
            line.globalStartOffset = segment.startOffset + segOffset;
            line.globalEndOffset = segment.startOffset + endIdx;
            m_lineLayout.add(line);
            
            currentY += lineHeight;
            
            // Move to next line
            if (newlineIdx != -1 && endIdx == newlineIdx) {
                segOffset = newlineIdx + 1; // Skip newline
            } else {
                segOffset = endIdx;
            }
            
            // Move to next segment if done with current
            if (segOffset >= text.length()) {
                segIdx++;
                segOffset = 0;
            }
        }
        
        // Update max scroll
        int paddingBottom = (int) m_insets.getBottom();
        int contentHeight = (int) (currentY + paddingBottom);
        m_maxScrollY = Math.max(0, contentHeight - m_preferredHeight);
        
        // Clamp scroll position
        m_scrollY = Math.max(0, Math.min(m_scrollY, m_maxScrollY));
    }
    
    private int findWrapPoint(String text, Font font, int maxWidth) {
        int low = 0;
        int high = text.length();
        int lastSpace = -1;
        
        // Find last space
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                lastSpace = i;
                break;
            }
        }
        
        // Binary search for wrap point
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String substr = text.substring(0, mid);
            int width = m_textRenderer.getTextWidth(substr, font);
            
            if (width <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        
        // Prefer breaking at space if close
        if (lastSpace > 0 && lastSpace >= low * 0.7) {
            return lastSpace + 1;
        }
        
        return Math.max(1, low);
    }
    
    // ========== Rendering ==========
    
    private void renderVisibleLines(Graphics2D g2d, int x, int y, int width, int height) {
        int firstLine = findFirstVisibleLine();
        int lastLine = findLastVisibleLine(height);
        
        for (int i = firstLine; i <= lastLine && i < m_lineLayout.size(); i++) {
            renderLine(g2d, m_lineLayout.get(i), x, y);
        }
    }
    
    private void renderLine(Graphics2D g2d, LineInfo line, int baseX, int baseY) {
        int y = (int) (line.y - m_scrollY + baseY);
        
        // Render selection background if needed
        if (hasSelection()) {
            renderLineSelection(g2d, line, baseX, y);
        }
        
        // Render text
        TextSegment segment = m_segmentCache.get(line.startSegment);
        String text = segment.getText().substring(line.startOffset, line.endOffset);
        
        g2d.setFont(segment.font);
        g2d.setColor(segment.textColor);
        
        FontMetrics metrics = m_textRenderer.getMetrics(segment.font);
        int textY = y + metrics.getAscent();
        
        g2d.drawString(text, baseX, textY);
    }
    
    private void renderLineSelection(Graphics2D g2d, LineInfo line, int baseX, int y) {
        if (m_selectionStart == null || m_selectionEnd == null) return;
        
        CursorPosition selStart = m_selectionStart;
        CursorPosition selEnd = m_selectionEnd;
        
        // Ensure start < end
        if (comparePositions(selStart, selEnd) > 0) {
            CursorPosition tmp = selStart;
            selStart = selEnd;
            selEnd = tmp;
        }
        
        int lineStartGlobal = line.globalStartOffset;
        int lineEndGlobal = line.globalEndOffset;
        
        int selStartGlobal = getGlobalOffset(selStart);
        int selEndGlobal = getGlobalOffset(selEnd);
        
        // Check if selection overlaps this line
        if (selEndGlobal < lineStartGlobal || selStartGlobal > lineEndGlobal) {
            return; // No overlap
        }
        
        // Calculate selection bounds within line
        int selStartInLine = Math.max(0, selStartGlobal - lineStartGlobal);
        int selEndInLine = Math.min(lineEndGlobal - lineStartGlobal, selEndGlobal - lineStartGlobal);
        
        TextSegment segment = m_segmentCache.get(line.startSegment);
        String lineText = segment.getText().substring(line.startOffset, line.endOffset);
        
        // Calculate pixel positions
        String beforeSel = lineText.substring(0, selStartInLine);
        String selectedText = lineText.substring(selStartInLine, selEndInLine);
        
        int beforeWidth = m_textRenderer.getTextWidth(beforeSel, segment.font);
        int selWidth = m_textRenderer.getTextWidth(selectedText, segment.font);
        
        g2d.setColor(m_selectionColor);
        g2d.fillRect(baseX + beforeWidth, y, selWidth, (int) line.height);
    }
    
    private void renderCursor(Graphics2D g2d, int baseX, int baseY) {
        LineInfo line = findLineForCursor();
        if (line == null) return;
        
        TextSegment segment = m_segmentCache.get(m_cursor.segmentIndex);
        String text = segment.getText();
        
        int offsetInLine = m_cursor.offsetInSegment - line.startOffset;
        String beforeCursor = text.substring(line.startOffset, 
            line.startOffset + offsetInLine);
        
        int cursorX = baseX + m_textRenderer.getTextWidth(beforeCursor, segment.font);
        int cursorY = (int) (line.y - m_scrollY + baseY);
        
        g2d.setColor(m_cursorColor);
        g2d.fillRect(cursorX, cursorY, 2, (int) line.height);
    }
    
    private int findFirstVisibleLine() {
        for (int i = 0; i < m_lineLayout.size(); i++) {
            LineInfo line = m_lineLayout.get(i);
            if (line.y + line.height >= m_scrollY) {
                return i;
            }
        }
        return 0;
    }
    
    private int findLastVisibleLine(int viewportHeight) {
        int maxY = m_scrollY + viewportHeight;
        for (int i = m_lineLayout.size() - 1; i >= 0; i--) {
            LineInfo line = m_lineLayout.get(i);
            if (line.y <= maxY) {
                return i;
            }
        }
        return m_lineLayout.size() - 1;
    }
    
    private LineInfo findLineForCursor() {
        int globalOffset = getGlobalOffset(m_cursor);
        
        for (LineInfo line : m_lineLayout) {
            if (globalOffset >= line.globalStartOffset && 
                globalOffset <= line.globalEndOffset) {
                return line;
            }
        }
        
        return m_lineLayout.isEmpty() ? null : m_lineLayout.get(m_lineLayout.size() - 1);
    }
    
    // ========== Cursor Management ==========
    
    private void setupCursorBlink() {
        m_cursorTimeline = new Timeline(
            new KeyFrame(Duration.millis(FxResourceFactory.CURSOR_DELAY), _ -> {
                m_cursorVisible = !m_cursorVisible;
                requestRender();
            })
        );
        m_cursorTimeline.setCycleCount(Timeline.INDEFINITE);
        
        focusedProperty().addListener((_, _, focused) -> {
            m_isFocused = focused;
            if (focused) {
                m_cursorVisible = true;
                m_cursorTimeline.play();
            } else {
                m_cursorTimeline.stop();
                m_cursorVisible = false;
                clearSelection();
            }
            requestRender();
        });
    }
    
    private void moveCursorLeft() {
        if (m_cursor.offsetInSegment > 0) {
            m_cursor.offsetInSegment--;
        } else if (m_cursor.segmentIndex > 0) {
            m_cursor.segmentIndex--;
            m_cursor.offsetInSegment = m_segmentCache.get(m_cursor.segmentIndex).length;
        }
        ensureCursorVisible();
        requestRender();
    }
    
    private void moveCursorRight() {
        TextSegment segment = m_segmentCache.get(m_cursor.segmentIndex);
        if (m_cursor.offsetInSegment < segment.length) {
            m_cursor.offsetInSegment++;
        } else if (m_cursor.segmentIndex < m_segmentCache.size() - 1) {
            m_cursor.segmentIndex++;
            m_cursor.offsetInSegment = 0;
        }
        ensureCursorVisible();
        requestRender();
    }
    
    private void moveCursorUp() {
        LineInfo currentLine = findLineForCursor();
        if (currentLine == null) return;
        
        int lineIndex = m_lineLayout.indexOf(currentLine);
        if (lineIndex > 0) {
            LineInfo prevLine = m_lineLayout.get(lineIndex - 1);
            moveCursorToLine(prevLine);
        }
    }
    
    private void moveCursorDown() {
        LineInfo currentLine = findLineForCursor();
        if (currentLine == null) return;
        
        int lineIndex = m_lineLayout.indexOf(currentLine);
        if (lineIndex < m_lineLayout.size() - 1) {
            LineInfo nextLine = m_lineLayout.get(lineIndex + 1);
            moveCursorToLine(nextLine);
        }
    }
    
    private void moveCursorToLine(LineInfo line) {
        // Try to maintain horizontal position
        TextSegment currentSeg = m_segmentCache.get(m_cursor.segmentIndex);
        String beforeCursor = currentSeg.getText().substring(0, m_cursor.offsetInSegment);
        int targetX = m_textRenderer.getTextWidth(beforeCursor, currentSeg.font);
        
        // Find position in target line
        TextSegment targetSeg = m_segmentCache.get(line.startSegment);
        String lineText = targetSeg.getText().substring(line.startOffset, line.endOffset);
        
        int bestOffset = 0;
        int bestDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i <= lineText.length(); i++) {
            String substr = lineText.substring(0, i);
            int x = m_textRenderer.getTextWidth(substr, targetSeg.font);
            int distance = Math.abs(x - targetX);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestOffset = i;
            }
        }
        
        m_cursor.segmentIndex = line.startSegment;
        m_cursor.offsetInSegment = line.startOffset + bestOffset;
        ensureCursorVisible();
        requestRender();
    }
    
    private void ensureCursorVisible() {
        LineInfo line = findLineForCursor();
        if (line == null) return;
        
        int paddingTop = (int) m_insets.getTop();
        int paddingBottom = (int) m_insets.getBottom();
        int viewportHeight = m_preferredHeight - paddingTop - paddingBottom;
        
        double cursorTop = line.y;
        double cursorBottom = line.y + line.height;
        
        if (cursorTop < m_scrollY) {
            m_scrollY = (int) cursorTop;
        } else if (cursorBottom > m_scrollY + viewportHeight) {
            m_scrollY = (int) (cursorBottom - viewportHeight);
        }
        
        m_scrollY = Math.max(0, Math.min(m_scrollY, m_maxScrollY));
    }
    
    // ========== Event Handlers ==========
    
    private void setupEventHandlers() {
        setOnKeyPressed(this::handleKeyPressed);
        setOnKeyTyped(this::handleKeyTyped);
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        setOnScroll(this::handleScroll);
    }
    
    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        boolean shift = event.isShiftDown();
        boolean ctrl = event.isControlDown();
        
        m_cursorVisible = true;
        m_cursorTimeline.playFromStart();
        
        if (code == KeyCode.LEFT) {
            if (shift) startSelection();
            else clearSelection();
            moveCursorLeft();
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.RIGHT) {
            if (shift) startSelection();
            else clearSelection();
            moveCursorRight();
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.UP) {
            if (shift) startSelection();
            else clearSelection();
            moveCursorUp();
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.DOWN) {
            if (shift) startSelection();
            else clearSelection();
            moveCursorDown();
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.HOME) {
            if (shift) startSelection();
            else clearSelection();
            if (ctrl) {
                moveCursorToStart();
            } else {
                moveCursorToLineStart();
            }
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.END) {
            if (shift) startSelection();
            else clearSelection();
            if (ctrl) {
                moveCursorToEnd();
            } else {
                moveCursorToLineEnd();
            }
            if (shift) updateSelection();
            event.consume();
        } else if (code == KeyCode.BACK_SPACE) {
            if (hasSelection()) {
                deleteSelection();
            } else {
                deleteBeforeCursor();
            }
            event.consume();
        } else if (code == KeyCode.DELETE) {
            if (hasSelection()) {
                deleteSelection();
            } else {
                deleteAfterCursor();
            }
            event.consume();
        } else if (code == KeyCode.ENTER) {
            if (hasSelection()) {
                deleteSelection();
            }
            insertAtCursor("\n");
            event.consume();
        } else if (code == KeyCode.A && ctrl) {
            selectAll();
            event.consume();
        } else if (code == KeyCode.TAB) {
            if (!shift) {
                if (hasSelection()) {
                    deleteSelection();
                }
                insertAtCursor("    ");
            }
            event.consume();
        }
    }
    
    private void handleKeyTyped(KeyEvent event) {
        String character = event.getCharacter();
        
        // Filter control characters
        if (character == null || character.isEmpty() || 
            character.charAt(0) < 32 || character.equals("\u007F")) {
            event.consume();
            return;
        }
        
        // Delete selection first if exists
        if (hasSelection()) {
            deleteSelection();
        }
        
        insertAtCursor(character);
        event.consume();
    }
    
    private void handleMousePressed(MouseEvent event) {
        requestFocus();
        
        double x = event.getX();
        double y = event.getY();
        
        CursorPosition pos = getCursorPositionFromPoint(x, y);
        if (pos != null) {
            m_cursor = pos;
            m_selectionStart = pos.copy();
            m_selectionEnd = pos.copy();
            m_isSelecting = true;
            m_cursorVisible = true;
            requestRender();
        }
    }
    
    private void handleMouseDragged(MouseEvent event) {
        if (m_isSelecting) {
            double x = event.getX();
            double y = event.getY();
            
            CursorPosition pos = getCursorPositionFromPoint(x, y);
            if (pos != null) {
                m_cursor = pos;
                m_selectionEnd = pos.copy();
                requestRender();
            }
        }
    }
    
    private void handleMouseReleased(MouseEvent event) {
        m_isSelecting = false;
        
        if (m_selectionStart != null && m_selectionEnd != null && 
            m_selectionStart.equals(m_selectionEnd)) {
            clearSelection();
            requestRender();
        }
    }
    
    private void handleScroll(ScrollEvent event) {
        double delta = event.getDeltaY();
        int scrollAmount = (int) (-delta);
        
        m_scrollY = Math.max(0, Math.min(m_scrollY + scrollAmount, m_maxScrollY));
        requestRender();
        event.consume();
    }
    
    private CursorPosition getCursorPositionFromPoint(double x, double y) {
        int paddingLeft = (int) m_insets.getLeft();
        int paddingTop = (int) m_insets.getTop();
        
        double adjustedY = y - paddingTop + m_scrollY;
        
        // Find line
        LineInfo targetLine = null;
        for (LineInfo line : m_lineLayout) {
            if (adjustedY >= line.y && adjustedY < line.y + line.height) {
                targetLine = line;
                break;
            }
        }
        
        if (targetLine == null) {
            // Click outside lines - return last position
            if (m_lineLayout.isEmpty()) {
                return new CursorPosition(0, 0);
            }
            if (adjustedY < m_lineLayout.get(0).y) {
                return new CursorPosition(0, 0);
            }
            LineInfo lastLine = m_lineLayout.get(m_lineLayout.size() - 1);
            return new CursorPosition(lastLine.endSegment, lastLine.endOffset);
        }
        
        // Find position within line
        TextSegment segment = m_segmentCache.get(targetLine.startSegment);
        String lineText = segment.getText().substring(
            targetLine.startOffset, targetLine.endOffset);
        
        double relativeX = x - paddingLeft;
        
        int bestOffset = 0;
        double bestDistance = Double.MAX_VALUE;
        
        for (int i = 0; i <= lineText.length(); i++) {
            String substr = lineText.substring(0, i);
            double charX = m_textRenderer.getTextWidth(substr, segment.font);
            double distance = Math.abs(charX - relativeX);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestOffset = i;
            }
        }
        
        return new CursorPosition(
            targetLine.startSegment, 
            targetLine.startOffset + bestOffset
        );
    }
    
    // ========== Text Operations ==========
    
    private void insertAtCursor(String text) {
        if (m_segmentCache.isEmpty()) {
            addSegment(new TextSegment("", m_defaultFont, m_defaultTextColor));
        }
        
        TextSegment segment = m_segmentCache.get(m_cursor.segmentIndex);
        segment.insertAt(m_cursor.offsetInSegment, text);
        
        // Update segment in storage
        m_segments.set(m_cursor.segmentIndex, segment.data);
        
        // Move cursor
        m_cursor.offsetInSegment += text.codePointCount(0, text.length());
        
        m_layoutDirty = true;
        requestRender();
    }
    
    private void deleteBeforeCursor() {
        if (m_cursor.offsetInSegment > 0) {
            TextSegment segment = m_segmentCache.get(m_cursor.segmentIndex);
            segment.deleteRange(m_cursor.offsetInSegment - 1, m_cursor.offsetInSegment);
            m_segments.set(m_cursor.segmentIndex, segment.data);
            m_cursor.offsetInSegment--;
            m_layoutDirty = true;
            requestRender();
        } else if (m_cursor.segmentIndex > 0) {
            // Merge with previous segment
            TextSegment prevSegment = m_segmentCache.get(m_cursor.segmentIndex - 1);
            TextSegment currentSegment = m_segmentCache.get(m_cursor.segmentIndex);
            
            int newOffset = prevSegment.length;
            prevSegment.insertAt(prevSegment.length, currentSegment.getText());
            
            m_segments.set(m_cursor.segmentIndex - 1, prevSegment.data);
            m_segments.remove(m_cursor.segmentIndex);
            m_segmentCache.remove(m_cursor.segmentIndex);
            
            m_cursor.segmentIndex--;
            m_cursor.offsetInSegment = newOffset;
            
            m_layoutDirty = true;
            requestRender();
        }
    }
    
    private void deleteAfterCursor() {
        TextSegment segment = m_segmentCache.get(m_cursor.segmentIndex);
        
        if (m_cursor.offsetInSegment < segment.length) {
            segment.deleteRange(m_cursor.offsetInSegment, m_cursor.offsetInSegment + 1);
            m_segments.set(m_cursor.segmentIndex, segment.data);
            m_layoutDirty = true;
            requestRender();
        } else if (m_cursor.segmentIndex < m_segmentCache.size() - 1) {
            // Merge with next segment
            TextSegment nextSegment = m_segmentCache.get(m_cursor.segmentIndex + 1);
            segment.insertAt(segment.length, nextSegment.getText());
            
            m_segments.set(m_cursor.segmentIndex, segment.data);
            m_segments.remove(m_cursor.segmentIndex + 1);
            m_segmentCache.remove(m_cursor.segmentIndex + 1);
            
            m_layoutDirty = true;
            requestRender();
        }
    }
    
    private void deleteSelection() {
        if (!hasSelection()) return;
        
        CursorPosition start = m_selectionStart;
        CursorPosition end = m_selectionEnd;
        
        // Ensure start < end
        if (comparePositions(start, end) > 0) {
            CursorPosition tmp = start;
            start = end;
            end = tmp;
        }
        
        // Same segment - simple delete
        if (start.segmentIndex == end.segmentIndex) {
            TextSegment segment = m_segmentCache.get(start.segmentIndex);
            segment.deleteRange(start.offsetInSegment, end.offsetInSegment);
            m_segments.set(start.segmentIndex, segment.data);
            
            m_cursor = start.copy();
            clearSelection();
            m_layoutDirty = true;
            requestRender();
            return;
        }
        
        // Multiple segments
        TextSegment startSegment = m_segmentCache.get(start.segmentIndex);
        TextSegment endSegment = m_segmentCache.get(end.segmentIndex);
        
        // Delete end of start segment
        startSegment.deleteRange(start.offsetInSegment, startSegment.length);
        
        // Append remainder of end segment
        String endText = endSegment.getText().substring(end.offsetInSegment);
        startSegment.insertAt(startSegment.length, endText);
        
        m_segments.set(start.segmentIndex, startSegment.data);
        
        // Remove segments in between and end segment
        for (int i = end.segmentIndex; i > start.segmentIndex; i--) {
            m_segments.remove(i);
            m_segmentCache.remove(i);
        }
        
        m_cursor = start.copy();
        clearSelection();
        m_layoutDirty = true;
        requestRender();
    }
    
    // ========== Selection Management ==========
    
    private boolean hasSelection() {
        return m_selectionStart != null && m_selectionEnd != null && 
               !m_selectionStart.equals(m_selectionEnd);
    }
    
    private void startSelection() {
        if (m_selectionStart == null) {
            m_selectionStart = m_cursor.copy();
            m_selectionEnd = m_cursor.copy();
        }
    }
    
    private void updateSelection() {
        m_selectionEnd = m_cursor.copy();
        requestRender();
    }
    
    private void clearSelection() {
        m_selectionStart = null;
        m_selectionEnd = null;
    }
    
    private void selectAll() {
        if (m_segmentCache.isEmpty()) return;
        
        m_selectionStart = new CursorPosition(0, 0);
        
        int lastSegIdx = m_segmentCache.size() - 1;
        TextSegment lastSeg = m_segmentCache.get(lastSegIdx);
        m_selectionEnd = new CursorPosition(lastSegIdx, lastSeg.length);
        
        m_cursor = m_selectionEnd.copy();
        requestRender();
    }
    
    private void moveCursorToStart() {
        m_cursor = new CursorPosition(0, 0);
        ensureCursorVisible();
        requestRender();
    }
    
    private void moveCursorToEnd() {
        if (m_segmentCache.isEmpty()) return;
        
        int lastIdx = m_segmentCache.size() - 1;
        TextSegment lastSeg = m_segmentCache.get(lastIdx);
        m_cursor = new CursorPosition(lastIdx, lastSeg.length);
        ensureCursorVisible();
        requestRender();
    }
    
    private void moveCursorToLineStart() {
        LineInfo line = findLineForCursor();
        if (line != null) {
            m_cursor = new CursorPosition(line.startSegment, line.startOffset);
            ensureCursorVisible();
            requestRender();
        }
    }
    
    private void moveCursorToLineEnd() {
        LineInfo line = findLineForCursor();
        if (line != null) {
            m_cursor = new CursorPosition(line.endSegment, line.endOffset);
            ensureCursorVisible();
            requestRender();
        }
    }
    
    // ========== Utility Methods ==========
    
    private int comparePositions(CursorPosition p1, CursorPosition p2) {
        if (p1.segmentIndex != p2.segmentIndex) {
            return Integer.compare(p1.segmentIndex, p2.segmentIndex);
        }
        return Integer.compare(p1.offsetInSegment, p2.offsetInSegment);
    }
    
    private int getGlobalOffset(CursorPosition pos) {
        int offset = 0;
        for (int i = 0; i < pos.segmentIndex; i++) {
            offset += m_segmentCache.get(i).length;
        }
        offset += pos.offsetInSegment;
        return offset;
    }
    
    // ========== Segment Management ==========
    
    private void addSegment(TextSegment segment) {
        m_segments.add(segment.data);
        m_segmentCache.add(segment);
        m_layoutDirty = true;
    }
    
    private void rebuildSegmentCache() {
        m_segmentCache.clear();
        
        for (int i = 0; i < m_segments.size(); i++) {
            NoteBytes item = m_segments.get(i);
            if (item instanceof NoteBytesObject) {
                m_segmentCache.add(new TextSegment((NoteBytesObject) item));
            }
        }
        
        m_layoutDirty = true;
    }
    
    // ========== Public API ==========
    
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (TextSegment segment : m_segmentCache) {
            sb.append(segment.getText());
        }
        return sb.toString();
    }
    
    public void setText(String text) {
        m_segments.clear();
        m_segmentCache.clear();
        
        addSegment(new TextSegment(text, m_defaultFont, m_defaultTextColor));
        
        m_cursor = new CursorPosition(0, 0);
        m_scrollY = 0;
        clearSelection();
        m_layoutDirty = true;
        requestRender();
    }
    
    public void clear() {
        m_segments.clear();
        m_segmentCache.clear();
        
        addSegment(new TextSegment("", m_defaultFont, m_defaultTextColor));
        
        m_cursor = new CursorPosition(0, 0);
        m_scrollY = 0;
        clearSelection();
        m_layoutDirty = true;
        requestRender();
    }
    
    public NoteBytesArray getSegments() {
        return m_segments;
    }
    
    public void setSegments(NoteBytesArray segments) {
        m_segments = segments;
        rebuildSegmentCache();
        m_cursor = new CursorPosition(0, 0);
        m_scrollY = 0;
        clearSelection();
        requestRender();
    }
    
    public String getSelectedText() {
        if (!hasSelection()) return "";
        
        CursorPosition start = m_selectionStart;
        CursorPosition end = m_selectionEnd;
        
        if (comparePositions(start, end) > 0) {
            CursorPosition tmp = start;
            start = end;
            end = tmp;
        }
        
        StringBuilder sb = new StringBuilder();
        
        if (start.segmentIndex == end.segmentIndex) {
            TextSegment segment = m_segmentCache.get(start.segmentIndex);
            sb.append(segment.getText().substring(start.offsetInSegment, end.offsetInSegment));
        } else {
            // Start segment
            TextSegment startSeg = m_segmentCache.get(start.segmentIndex);
            sb.append(startSeg.getText().substring(start.offsetInSegment));
            
            // Middle segments
            for (int i = start.segmentIndex + 1; i < end.segmentIndex; i++) {
                sb.append(m_segmentCache.get(i).getText());
            }
            
            // End segment
            TextSegment endSeg = m_segmentCache.get(end.segmentIndex);
            sb.append(endSeg.getText().substring(0, end.offsetInSegment));
        }
        
        return sb.toString();
    }
    
    // ========== Configuration ==========
    
    public void setDefaultFont(Font font) {
        m_defaultFont = font;
        m_layoutDirty = true;
        requestRender();
    }
    
    public void setDefaultTextColor(Color color) {
        m_defaultTextColor = color;
        requestRender();
    }
    
    public void setCursorColor(Color color) {
        m_cursorColor = color;
        requestRender();
    }
    
    public void setSelectionColor(Color color) {
        m_selectionColor = color;
        requestRender();
    }
    
    public void setSelectedTextColor(Color color) {
        m_selectedTextColor = color;
        requestRender();
    }
    
    public void setBackgroundColor(Color color) {
        m_backgroundColor = color;
        requestRender();
    }
    
    public void setInsets(Insets insets) {
        m_insets = insets;
        m_layoutDirty = true;
        requestRender();
    }
    
    public Insets getInsets() {
        return m_insets;
    }
    
    // ========== Cleanup ==========
    
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(()->{
            if (m_cursorTimeline != null) {
                m_cursorTimeline.stop();
            }
            
            if (widthListener != null) {
                widthProperty().removeListener(widthListener);
            }
            if (heightListener != null) {
                heightProperty().removeListener(heightListener);
            }
            
            m_segments = null;
            m_segmentCache.clear();
            m_lineLayout.clear();
        }, TaskUtils.getVirtualExecutor()).thenAccept((_)->super.shutdown());
    }
}