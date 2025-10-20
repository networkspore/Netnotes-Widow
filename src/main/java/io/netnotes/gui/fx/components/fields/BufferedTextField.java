package io.netnotes.gui.fx.components.fields;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.gui.fx.components.images.BufferedCanvasView;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.GraphicsContextPool;
import io.netnotes.gui.fx.display.TextRenderer;
import io.netnotes.gui.fx.input.InputHelpers;
import io.netnotes.gui.fx.input.InputMask;

import java.awt.*;
import java.math.BigDecimal;

/**
 * Custom text field with direct rendering using BufferedCanvasView.
 * Features viewport scrolling, text selection, formatting, and validation.
 */
public class BufferedTextField extends BufferedCanvasView {
    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 40;
    private static final int VIEWPORT_BUFFER = GraphicsContextPool.SIZE_TOLERANCE; // Extra pixels in viewport to avoid frequent resizing
    
    private static final int LINEAR_SEARCH_MAX = 1000;
    
    
    public enum SizeMode {
        FIXED,
        HGROW,
        VGROW,
        HGROW_VGROW
    }

   /**
     * Unified rendering cache that shares glyph boundaries and computed values
     * across all rendering and interaction methods within a frame.
     * 
     * Lifecycle:
     * 1. prepareRenderCache() - Called at start of drawContent()
     * 2. Various methods use cached values during the frame
     * 3. invalidateRenderCache() - Called at end of drawContent() and on text changes
     */
    private static class RenderCache {
        // Visible text state
        String visibleText;
        int visibleStartOffset;  // m_viewportOffset when cache was built
        int visibleCodePointCount;
        
        // Layout dimensions
        int textX;
        int textY;
        int paddingLeft;
        int paddingRight;
        int paddingTop;
        int paddingBottom;
        int availableWidth;
        int availableHeight;
        
        // Glyph measurements
        double[] glyphBoundaries;  // Cumulative widths for each code point
        double totalWidth;
        
        // Cache validity
        boolean valid;
        
        void invalidate() {
            valid = false;
            visibleText = null;
            glyphBoundaries = null;
        }
        
        boolean isValid() {
            return valid && visibleText != null && glyphBoundaries != null;
        }
    }

    private final RenderCache m_renderCache = new RenderCache();
    
    // Text storage
    private NoteIntegerArray m_text = new NoteIntegerArray();
    private int m_cursorPosition = 0; // Code point index

  

    // Text selection
    private int m_selectionStart = -1;
    private int m_selectionEnd = -1;
    private boolean m_isSelecting = false;
    
    // Viewport for horizontal scrolling
    private int m_viewportOffset = 0; // Code point offset
    private int m_scrollOffset = 0; // Pixel offset for CENTER/RIGHT alignment
    
    // Visual state
    private boolean m_cursorVisible = true;
    private boolean m_isFocused = false;
    
    // Cursor animation
    private Timeline m_cursorTimeline;
    
    // Styling
    private Font m_font = new Font("Monospaced", Font.PLAIN, 14);
    private Color m_textColor = Color.BLACK;
    private Color m_cursorColor = new Color(59, 130, 246); // Blue
    private Color m_selectionColor = new Color(59, 130, 246); // Blue
    private Color m_selectedTextColor = Color.WHITE;
    private TextAlignment m_textAlignment = TextAlignment.LEFT;
    
    // Placeholder
    private String m_placeholderText = "";
    private Color m_placeholderTextColor = new Color(150, 150, 150); // Gray
    
    // Dimensions and viewport
    private int m_preferredWidth = DEFAULT_WIDTH;
    private int m_preferredHeight = DEFAULT_HEIGHT;
    private int m_viewportWidth = DEFAULT_WIDTH + VIEWPORT_BUFFER;
    private int m_viewportHeight = DEFAULT_HEIGHT;
    private int m_minWidth = 50;
    private int m_maxWidth = Integer.MAX_VALUE;
    private int m_minHeight = 50;
    private int m_maxHeight = Integer.MAX_VALUE;
    private SizeMode m_sizeMode = SizeMode.FIXED;
    
    // Padding
    private Insets m_insets = new Insets(0);
    private int m_margin = 20;
    
    // Validation and formatting
    private TextValidator m_validator = null;
    private InputMask m_inputMask = null;
    
    // Text renderer (singleton)
    private final TextRenderer m_textRenderer = TextRenderer.getInstance();
    
    // Layout listeners
    private ChangeListener<Number> widthListener = null;
    private ChangeListener<Number> heightListener = null;

    // ========== Functional Interfaces ==========
    
    @FunctionalInterface
    public interface TextValidator {
        boolean validate(String text);
    }
    
    // ========== Constructors ==========
    
    private BufferedTextField(boolean renderImmediate) {
        super();
        setRenderMode(RenderMode.GENERATE);
        setFocusTraversable(true);
        
        // Calculate initial height from font
        updateHeightFromFont();

        setupCursorBlink();
        setupEventHandlers();
        setupLayoutListeners();
        if(renderImmediate){
            requestRender();
        }
    }

    public BufferedTextField() {
        this(true);
    }
    
    public BufferedTextField(int width, int height) {
        this(false);
        setSizeMode(SizeMode.FIXED, false);
        setPreferredSize(width, height);
    }
    

    public Insets getInsets(){
        return m_insets;
    }

    public void setInsets(Insets insets){
        m_insets = insets;
        invalidateRenderCache();
        requestRender();
    }

    public int getMargin(){
        return m_margin;
    }

    public void setMargin(int margin){
        m_margin = margin;
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
    
    /**
     * Calculate height from font metrics and padding
     */
    public void updateHeightFromFont() {
        FontMetrics metrics = m_textRenderer.getMetrics(m_font);
        
        setPrefHeight(metrics.getHeight() + getInsets().getTop() + getInsets().getBottom());
    }

    /**
     * Setup listeners for layout changes
     */
    private void setupLayoutListeners() {
  
        if(isHgrow()){
            if(widthListener == null){
                widthListener = (_, _,newVal)-> {
                    int val = (int) Math.ceil(newVal.doubleValue());
                    if (isHgrow() && val > 0) {
                        setPrefWidth(val);
                    }
                };
                widthProperty().addListener(widthListener);
            }
        }else{
            if(widthListener != null){
                widthProperty().removeListener(widthListener);
                widthListener = null;
            }
        }
        if(isVgrow()){
            if(heightListener == null){
                heightListener = (_, _, newVal) -> {
                    double val = newVal.doubleValue();
                    if (isVgrow()) {
                        setPrefHeight(val);
                    }
                };
                heightProperty().addListener(heightListener);
            }
        }else{
            if(heightListener != null){
                heightProperty().removeListener(heightListener);
                heightListener = null;
            }
        }
         
        
    }
    
    public boolean isHgrow(){
        return m_sizeMode == SizeMode.HGROW || m_sizeMode == SizeMode.HGROW_VGROW;
    }

    public boolean isVgrow(){
        return m_sizeMode == SizeMode.VGROW || m_sizeMode == SizeMode.HGROW_VGROW;
    }
  
    
    @Override
    public double prefWidth(double height) {
        return m_preferredWidth;
    }
    
    @Override
    public double prefHeight(double width) {
        return m_preferredHeight;
    }
    
    @Override
    public double minWidth(double height) {
        return m_minWidth;
    }
    
    @Override
    public double maxWidth(double height) {
        return m_maxWidth;
    }

    /**
     * Calculate X position based on text alignment (extracted from getTextX)
     */
    private int calculateTextX(int textWidth, int paddingLeft, int availableWidth) {
        switch (m_textAlignment) {
            case CENTER:
                return paddingLeft + Math.max(0, (availableWidth - textWidth) / 2) - m_scrollOffset;
            case RIGHT:
                return paddingLeft + Math.max(0, availableWidth - textWidth) - m_scrollOffset;
            case LEFT:
            default:
                return paddingLeft;
        }
    }


   /**
     * Prepare render cache with all values needed for this frame.
     * Called once at the start of drawContent().
     * 
     * This is the single source of truth for visible text and glyph boundaries
     * during a render pass.
     */
    private void prepareRenderCache() {
        // Get layout dimensions
        Insets insets = getInsets();
        m_renderCache.paddingLeft = (int) insets.getLeft();
        m_renderCache.paddingRight = (int) insets.getRight();
        m_renderCache.paddingTop = (int) insets.getTop();
        m_renderCache.paddingBottom = (int) insets.getBottom();
        
        m_renderCache.availableWidth = m_preferredWidth - m_renderCache.paddingLeft - m_renderCache.paddingRight;
        m_renderCache.availableHeight = m_preferredHeight - m_renderCache.paddingTop - m_renderCache.paddingBottom;
        
        // Calculate text Y position
        FontMetrics metrics = m_textRenderer.getMetrics(m_font);
        m_renderCache.textY = m_renderCache.paddingTop + 
            (m_renderCache.availableHeight + metrics.getAscent() - metrics.getDescent()) / 2;
        
        // Get visible text and viewport state
        m_renderCache.visibleText = getVisibleText();
        m_renderCache.visibleStartOffset = m_viewportOffset;
        
        if (m_renderCache.visibleText.isEmpty()) {
            m_renderCache.textX = m_renderCache.paddingLeft;
            m_renderCache.totalWidth = 0;
            m_renderCache.glyphBoundaries = new double[]{0.0};
            m_renderCache.visibleCodePointCount = 0;
            m_renderCache.valid = true;
            return;
        }
        
        // Calculate glyph boundaries
        m_renderCache.glyphBoundaries = calculateGlyphBoundaries(m_renderCache.visibleText, m_font);
        m_renderCache.visibleCodePointCount = m_renderCache.glyphBoundaries.length - 1;
        m_renderCache.totalWidth = m_renderCache.glyphBoundaries[m_renderCache.visibleCodePointCount];
        
        // Calculate text X position based on alignment
        m_renderCache.textX = calculateTextX((int) m_renderCache.totalWidth, 
            m_renderCache.paddingLeft, m_renderCache.availableWidth);
        
        m_renderCache.valid = true;
    }

     /**
     * Invalidate render cache (call on text changes, font changes, or layout changes)
     */
    private void invalidateRenderCache() {
        m_renderCache.invalidate();
    }
    
    @Override
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Update viewport to keep cursor visible
        updateViewport();
        
        // Prepare render cache ONCE for this frame
        prepareRenderCache();
        
        // Clear background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Set font
        g2d.setFont(m_font);
        
        // Set clipping ONCE to visible area
        g2d.setClip(m_renderCache.paddingLeft, m_renderCache.paddingTop, 
                    m_renderCache.availableWidth, m_renderCache.availableHeight);
        
        // Draw placeholder, text, or cursor
        if (m_text.isEmpty() && !m_isFocused && !m_placeholderText.isEmpty()) {
            drawPlaceholder(g2d);
        } else if (!m_text.isEmpty()) {
            drawTextWithSelection(g2d);
        }
        
        // Draw cursor if focused and visible
        if (m_isFocused && m_cursorVisible && !hasSelection() && isCursorInViewport()) {
            drawCursor(g2d);
        }
        
        // Reset clip
        g2d.setClip(null);
    }
    
    /**
     * Draw placeholder text using render cache
     */
    private void drawPlaceholder(Graphics2D g2d) {
        g2d.setColor(m_placeholderTextColor);
        int x = calculateTextX(
            m_textRenderer.getTextWidth(m_placeholderText, m_font), 
            m_renderCache.paddingLeft, 
            m_renderCache.availableWidth
        );
        g2d.drawString(m_placeholderText, x, m_renderCache.textY);
    }
    
    /**
     * Optimized text drawing with selection highlighting
     * Uses render cache for all measurements
     */
    private void drawTextWithSelection(Graphics2D g2d) {
        String visibleText = m_renderCache.visibleText;
        int textX = m_renderCache.textX;
        int textY = m_renderCache.textY;
        double[] boundaries = m_renderCache.glyphBoundaries;
        
        if (!hasSelection() || !isSelectionInViewport()) {
            // No selection - simple single draw
            g2d.setColor(m_textColor);
            g2d.drawString(visibleText, textX, textY);
            return;
        }
        
        // Calculate selection boundaries
        int visibleStart = m_renderCache.visibleStartOffset;
        int visibleEnd = visibleStart + m_renderCache.visibleCodePointCount;
        
        int selStart = Math.max(m_selectionStart, visibleStart);
        int selEnd = Math.min(m_selectionEnd, visibleEnd);
        
        // Early exit if selection outside viewport
        if (selEnd <= visibleStart || selStart >= visibleEnd) {
            g2d.setColor(m_textColor);
            g2d.drawString(visibleText, textX, textY);
            return;
        }
        
        // Convert to viewport-relative indices
        int relativeSelStart = selStart - visibleStart;
        int relativeSelEnd = selEnd - visibleStart;
        
        // Use glyph boundaries for positioning
        double beforeSelWidth = boundaries[relativeSelStart];
        double selectionWidth = boundaries[relativeSelEnd] - beforeSelWidth;
        
        int currentX = textX;
        
        // 1. Draw text before selection
        if (relativeSelStart > 0) {
            String beforeSel = visibleText.substring(0, 
                visibleText.offsetByCodePoints(0, relativeSelStart));
            g2d.setColor(m_textColor);
            g2d.drawString(beforeSel, currentX, textY);
            currentX += (int) beforeSelWidth;
        }
        
        // 2. Draw selection background
        g2d.setColor(m_selectionColor);
        g2d.fillRect(currentX, m_renderCache.paddingTop, 
                    (int) Math.ceil(selectionWidth), m_renderCache.availableHeight);
        
        // 3. Draw selected text
        int selStartOffset = visibleText.offsetByCodePoints(0, relativeSelStart);
        int selEndOffset = visibleText.offsetByCodePoints(0, relativeSelEnd);
        String selectedText = visibleText.substring(selStartOffset, selEndOffset);
        g2d.setColor(m_selectedTextColor);
        g2d.drawString(selectedText, currentX, textY);
        currentX += (int) selectionWidth;
        
        // 4. Draw text after selection
        if (relativeSelEnd < m_renderCache.visibleCodePointCount) {
            String afterSel = visibleText.substring(selEndOffset);
            g2d.setColor(m_textColor);
            g2d.drawString(afterSel, currentX, textY);
        }
    }
    
    /**
     * Optimized cursor drawing using render cache
     */
    private void drawCursor(Graphics2D g2d) {
        int visualCursorPos = m_cursorPosition - m_renderCache.visibleStartOffset;
        
        // Use pre-calculated glyph boundaries
        double[] boundaries = m_renderCache.glyphBoundaries;
        double cursorOffset = (visualCursorPos < boundaries.length) 
            ? boundaries[visualCursorPos] 
            : boundaries[boundaries.length - 1];
        
        int cursorX = m_renderCache.textX + (int) cursorOffset;
        
        g2d.setColor(m_cursorColor);
        g2d.fillRect(cursorX, m_renderCache.paddingTop, 2, m_renderCache.availableHeight);
    }
    

    // ========== Selection Management ==========
    
    private boolean hasSelection() {
        return m_selectionStart != -1 && m_selectionEnd != -1 && m_selectionStart != m_selectionEnd;
    }
    
    private boolean isSelectionInViewport() {
        if (!hasSelection()) return false;
        
        String visibleText = getVisibleText();
        int visibleStart = m_viewportOffset;
        int visibleEnd = visibleStart + visibleText.codePointCount(0, visibleText.length());
        
        return !(m_selectionEnd <= visibleStart || m_selectionStart >= visibleEnd);
    }
    
    private void clearSelection() {
        m_selectionStart = -1;
        m_selectionEnd = -1;
    }
    
    private void deleteSelection() {
        if (hasSelection()) {
            int start = Math.min(m_selectionStart, m_selectionEnd);
            int end = Math.max(m_selectionStart, m_selectionEnd);
            
            m_text.delete(start, end);
            m_cursorPosition = start;
            clearSelection();
            invalidateRenderCache();
        }
    }
    
    // ========== Viewport Management ==========
    
    private void updateViewport() {
        if (m_text.length() == 0) {
            m_viewportOffset = 0;
            m_scrollOffset = 0;
            return;
        }
        String newVisibleText = getVisibleText(); // Calculate new visible text
        
        // Only invalidate if visible portion changed
        if (!m_renderCache.isValid() || !newVisibleText.equals(m_renderCache.visibleText)) {
            invalidateRenderCache();
        }
        
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        
        // LEFT alignment - traditional scrolling
        if (m_textAlignment == TextAlignment.LEFT) {
            m_scrollOffset = 0;
            
            // If cursor is before viewport, scroll left
            if (m_cursorPosition < m_viewportOffset) {
                m_viewportOffset = m_cursorPosition;
                return;
            }
            
            // Check if cursor is beyond visible area, scroll right
            while (m_cursorPosition > m_viewportOffset) {
                String textToCursor = m_text.substring(
                    m_viewportOffset, m_cursorPosition).toString();
                int textWidth = m_textRenderer.getTextWidth(textToCursor, m_font);
                
                if (textWidth <= availableWidth - 4) {
                    break;
                }
                
                m_viewportOffset++;
            }
        } 
        // CENTER/RIGHT alignment - pixel-based scrolling
        else {
            m_viewportOffset = 0;
            String fullText = m_text.toString();
            int fullWidth = m_textRenderer.getTextWidth(fullText, m_font);
            
            // If text fits, center/right align naturally (no scroll needed)
            if (fullWidth <= availableWidth) {
                m_scrollOffset = 0;
                return;
            }
            
            // Calculate cursor position in pixels
            String textBeforeCursor = m_text.substring(0, m_cursorPosition).toString();
            int cursorPixelPos = m_textRenderer.getTextWidth(textBeforeCursor, m_font);
            
            // Calculate where cursor would appear with current scroll
            int displayPos = 0;
            if (m_textAlignment == TextAlignment.CENTER) {
                displayPos = (availableWidth - fullWidth) / 2 + cursorPixelPos - m_scrollOffset;
            } else { // RIGHT
                displayPos = availableWidth - fullWidth + cursorPixelPos - m_scrollOffset;
            }
            
            // Adjust scroll to keep cursor visible with some margin
            int margin = m_margin;
            if (displayPos < margin) {
                m_scrollOffset += (displayPos - margin);
            } else if (displayPos > availableWidth - margin) {
                m_scrollOffset += (displayPos - availableWidth + margin);
            }
            
            // Constrain scroll offset to valid range
            // Min: Allow scrolling right until the end of text aligns with right edge
            int minScroll = fullWidth - availableWidth;
            // Max: Allow scrolling left until start of text is visible
            int maxScroll = 0;
            m_scrollOffset = Math.max(minScroll, Math.min(maxScroll, m_scrollOffset));
        }
    }
    
    private String getVisibleText() {
        if (m_text.length() == 0) {
            return "";
        }
        
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        
        // For CENTER/RIGHT with scrolling, show all text
        if (m_textAlignment != TextAlignment.LEFT && m_scrollOffset > 0) {
            return m_text.toString();
        }
        
        // For LEFT or CENTER/RIGHT without scrolling
        if (m_textAlignment != TextAlignment.LEFT) {
            String fullText = m_text.toString();
            int fullWidth = m_textRenderer.getTextWidth(fullText, m_font);
            
            if (fullWidth <= availableWidth) {
                return fullText;
            }
        }
        
        int endPos = m_viewportOffset;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = m_textRenderer.getTextWidth(chunk, m_font);
            
            if (width > availableWidth) {
                break;
            }
            endPos++;
        }
        
        return m_text.substring(m_viewportOffset, endPos).toString();
    }
    
    /**
     * Check if cursor is in viewport using render cache if available
     */
    private boolean isCursorInViewport() {
        if (m_renderCache.isValid()) {
            int visualCursorPos = m_cursorPosition - m_renderCache.visibleStartOffset;
            return visualCursorPos >= 0 && visualCursorPos <= m_renderCache.visibleCodePointCount;
        }
        
        // Fallback to old calculation if cache not available
        int endPos = m_viewportOffset;
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = m_textRenderer.getTextWidth(chunk, m_font);
            if (width > availableWidth) break;
            endPos++;
        }
        
        return m_cursorPosition >= m_viewportOffset && m_cursorPosition <= endPos;
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
    
    // ========== Event Handlers ==========
    
    private void setupEventHandlers() {
        setOnKeyPressed(this::handleKeyPressed);
        setOnKeyTyped(this::handleKeyTyped);
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
    }
    
    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        boolean shift = event.isShiftDown();
        
        m_cursorVisible = true;
        m_cursorTimeline.playFromStart();
        
        if (code == KeyCode.LEFT) {
            if (shift) {
                startOrUpdateSelection();
                moveCursorLeft();
                updateSelection();
            } else {
                clearSelection();
                moveCursorLeft();
            }
            event.consume();
        } else if (code == KeyCode.RIGHT) {
            if (shift) {
                startOrUpdateSelection();
                moveCursorRight();
                updateSelection();
            } else {
                clearSelection();
                moveCursorRight();
            }
            event.consume();
        } else if (code == KeyCode.HOME) {
            if (shift) {
                startOrUpdateSelection();
            } else {
                clearSelection();
            }
            m_cursorPosition = 0;
            if (shift) {
                updateSelection();
            }
            requestRender();
            event.consume();
        } else if (code == KeyCode.END) {
            if (shift) {
                startOrUpdateSelection();
            } else {
                clearSelection();
            }
            m_cursorPosition = m_text.length();
            if (shift) {
                updateSelection();
            }
            requestRender();
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
        } else if (code == KeyCode.A && event.isControlDown()) {
            selectAll();
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
        
        // Ensure cache is prepared for cursor estimation
        if (!m_renderCache.isValid()) {
            prepareRenderCache();
        }
        
        double x = event.getX();
        int clickedPos = estimateCursorPosition(x);
        
        m_cursorPosition = clickedPos;
        m_selectionStart = clickedPos;
        m_selectionEnd = clickedPos;
        m_isSelecting = true;
        m_cursorVisible = true;
        
        requestRender();
    }
    
    private void handleMouseDragged(MouseEvent event) {
        if (m_isSelecting) {
            // Cache should still be valid from mousePressed or last render
            if (!m_renderCache.isValid()) {
                prepareRenderCache();
            }
            
            double x = event.getX();
            int dragPos = estimateCursorPosition(x);
            
            m_cursorPosition = dragPos;
            m_selectionEnd = dragPos;
            
            requestRender();
        }
    }
    
    private void handleMouseReleased(MouseEvent event) {
        m_isSelecting = false;
        
        if (m_selectionStart == m_selectionEnd) {
            clearSelection();
            requestRender();
        }
    }
    
    private void startOrUpdateSelection() {
        if (m_selectionStart == -1) {
            m_selectionStart = m_cursorPosition;
            m_selectionEnd = m_cursorPosition;
        }
    }
    
    private void updateSelection() {
        m_selectionEnd = m_cursorPosition;
        requestRender();
    }
    
    private void selectAll() {
        m_selectionStart = 0;
        m_selectionEnd = m_text.length();
        m_cursorPosition = m_text.length();
        requestRender();
    }
    
    // ========== Text Operations ==========
    
    private void insertAtCursor(String str) {
        // Build test text
        String testText = m_text.substring(0, m_cursorPosition).toString() 
                        + str 
                        + m_text.substring(m_cursorPosition).toString();
        
        // Validate with validator
        if (m_validator != null && !m_validator.validate(testText)) {
            return;
        }
        
        // Validate with input mask
        if (m_inputMask != null && !m_inputMask.isValidInput(str)) {
            return;
        }
        
        m_text.insert(m_cursorPosition, str);
        m_cursorPosition += str.codePointCount(0, str.length());
        
        // Apply formatting if mask is set
        if (m_inputMask != null) {
            applyInputMask();
        }
        
        invalidateRenderCache(); 
        requestRender();
    }
    
    /**
     * Apply input mask formatting and adjust cursor position intelligently
     */
    private void applyInputMask() {
        if (m_inputMask == null) {
            return;
        }
        
        String original = m_text.toString();
        String formatted = m_inputMask.format(original);
        
        if (!formatted.equals(original)) {
            // Calculate new cursor position using mask's cursor tracker
            int newCursorPos = m_inputMask.calculateCursorPosition(
                original, 
                m_cursorPosition, 
                formatted
            );
            
            // Update text
            m_text = new NoteIntegerArray(formatted);
            
            // Update cursor position
            m_cursorPosition = Math.max(0, Math.min(newCursorPos, m_text.length()));
            
            // Invalidate render cache since text changed
            invalidateRenderCache();
        }
    }
    
    private void deleteBeforeCursor() {
        if (m_cursorPosition > 0) {
            m_text.deleteCodePointAt(m_cursorPosition - 1);
            m_cursorPosition--;
            invalidateRenderCache();
            requestRender();
        }
    }
    
    private void deleteAfterCursor() {
        if (m_cursorPosition < m_text.length()) {
            m_text.deleteCodePointAt(m_cursorPosition);
            invalidateRenderCache();
            requestRender();
        }
    }
    
    private void moveCursorLeft() {
        if (m_cursorPosition > 0) {
            m_cursorPosition--;
            requestRender();
        }
    }
    
    private void moveCursorRight() {
        if (m_cursorPosition < m_text.length()) {
            m_cursorPosition++;
            requestRender();
        }
    }


    /**
     * Calculate glyph boundaries for the given text.
     * Returns an array where index i contains the cumulative width up to code point i.
     * 
     * Example: "Hi" → [0.0, 8.5, 14.2]
     *   - boundaries[0] = 0 (start)
     *   - boundaries[1] = 8.5 (after 'H')
     *   - boundaries[2] = 14.2 (after 'i')
     * 
     * @param text The text to measure
     * @param font The font to use
     * @return Array of cumulative widths (length = codePointCount + 1)
     */
    private double[] calculateGlyphBoundaries(String text, Font font) {
        if (text.isEmpty()) {
            return new double[]{0.0};
        }
        
        int codePointCount = text.codePointCount(0, text.length());
        double[] boundaries = new double[codePointCount + 1];
        
        double cumulativeWidth = 0.0;
        int boundaryIndex = 0;
        int charIndex = 0;
        
        boundaries[boundaryIndex++] = 0.0; // Start position
        
        while (charIndex < text.length()) {
            int codePoint = text.codePointAt(charIndex);
            int charCount = Character.charCount(codePoint);
            int nextIndex = charIndex + charCount;
            
            // Measure the individual glyph
            // Note: For ligatures, we measure from start to handle them correctly
            String glyphStr = text.substring(charIndex, nextIndex);
            double glyphWidth = m_textRenderer.getTextWidthPrecise(glyphStr, font);
            
            cumulativeWidth += glyphWidth;
            boundaries[boundaryIndex++] = cumulativeWidth;
            
            charIndex = nextIndex;
        }
        
        return boundaries;
    }

    


    private int estimateCursorPosition(double x){
        if (!m_renderCache.isValid()) {
            prepareRenderCache();
        }
        if(m_text.byteLength() > Integer.BYTES * LINEAR_SEARCH_MAX){
            return estimateCursorPositionBinary(x);
        }else{
            return estimateCursorPositionLinear(x);
        }
    }

    
    /**
     * Estimate cursor position from mouse click X coordinate.
     * Uses pre-computed glyph boundaries for O(n) performance.
     * 
     * @param x Mouse X position relative to text area (after padding adjustment)
     * @return Code point index in visible text
     */
    private int estimateCursorPositionBinary(double x) {
      
        String visibleText = m_renderCache.visibleText;
        
        if (visibleText.isEmpty()) {
            return m_viewportOffset;
        }
        
        
        double relativeX = x - m_renderCache.textX;

        // Get or compute glyph boundaries
        double[] boundaries = m_renderCache.glyphBoundaries;
        int codePointCount = m_renderCache.visibleCodePointCount;

        // Early exit: Click before text start
        if (relativeX <= boundaries[0]) {
            return m_viewportOffset;
        }

        // Early exit: Click after text end
        if (relativeX >= boundaries[codePointCount]) {
            return m_viewportOffset + codePointCount;
        }

        // Binary search for nearest boundary (O(log n))
        // This is more efficient than linear scan for long text
        int left = 0;
        int right = codePointCount;
        
        while (left < right) {
            int mid = (left + right) / 2;
            double midPoint = (boundaries[mid] + boundaries[mid + 1]) / 2.0;
            
            if (relativeX < midPoint) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return m_viewportOffset + left;
    }
    
   
    private int estimateCursorPositionLinear(double x) {

        String visibleText = m_renderCache.visibleText;
        
        if (visibleText.isEmpty()) {
            return m_viewportOffset;
        }
    
        double relativeX = x - m_renderCache.textX;

        // Get or compute glyph boundaries
        double[] boundaries = m_renderCache.glyphBoundaries;
        int codePointCount = m_renderCache.visibleCodePointCount;
    

        // Early exits
        if (relativeX <= boundaries[0]) {
            return m_viewportOffset;
        }
        if (relativeX >= boundaries[codePointCount]) {
            return m_viewportOffset + codePointCount;
        }

        // Linear search: find first boundary where midpoint > relativeX
        for (int i = 0; i < codePointCount; i++) {
            double midPoint = (boundaries[i] + boundaries[i + 1]) / 2.0;
            if (relativeX < midPoint) {
                return m_viewportOffset + i;
            }
        }

        return m_viewportOffset + codePointCount;
    }

    // ========== Public API ==========
    
    public String getText() {
        return m_text.toString();
    }
    
    /**
     * Update setText to invalidate cache
     */
    public void setText(String newText) {
        m_text = new NoteIntegerArray(newText);
        m_cursorPosition = m_text.length();
        m_viewportOffset = 0;
        m_scrollOffset = 0;
        clearSelection();
        invalidateRenderCache();
        requestRender();
    }
    
    public void clear() {
        m_text.clear();
        m_cursorPosition = 0;
        m_viewportOffset = 0;
        m_scrollOffset = 0;
        clearSelection();
        invalidateRenderCache();
        requestRender();
    }
    
    public NoteIntegerArray getTextAsNoteIntegerArray() {
        return m_text;
    }
    
    public String getSelectedText() {
        if (hasSelection()) {
            int start = Math.min(m_selectionStart, m_selectionEnd);
            int end = Math.max(m_selectionStart, m_selectionEnd);
            return m_text.substring(start, end).toString();
        }
        return "";
    }
    
    /**
     * Get text as BigDecimal using InputHelpers
     */
    public BigDecimal getTextAsBigDecimal(int decimals) {
        String text = getText();
        if (text.isEmpty() || InputHelpers.isTextZero(text)) {
            return BigDecimal.ZERO;
        }
        
        String formatted = InputHelpers.formatStringToNumber(text, decimals);
        try {
            return new BigDecimal(formatted);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Get text as int using InputHelpers
     */
    public int getTextAsInt() {
        String text = getText();
        if (text.isEmpty() || InputHelpers.isTextZero(text)) {
            return 0;
        }
        
        String formatted = InputHelpers.formatStringToNumber(text, 0);
        try {
            return Integer.parseInt(formatted);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    // ========== Configuration ==========
    public void setPrefHeight(double height){
        setPrefHeight(height, true);
    }
    
    public void setPrefHeight(double height, boolean render) {
        m_preferredHeight = (int) Math.ceil(height);
        invalidateRenderCache();
        updateViewPortHeight(render);
    }

    public void setPrefWidth(double width){
        setPrefWidth(width, true);
    }
    
    public void setPrefWidth(double width, boolean render) {
        int val = (int) Math.ceil(width);
        m_preferredWidth = val;
        invalidateRenderCache();
        updateViewPortWidth(render);
    }

    private void updateViewPortHeight(boolean render){
        int clamped = (int) Math.max(m_minHeight, Math.min(m_preferredHeight, m_maxHeight));
        int prevHeight = m_viewportHeight;
        m_viewportHeight = clamped;
        if(render && prevHeight != m_viewportHeight){
            requestRender();
        }
    }

    /**
     * Update viewport width with hysteresis to prevent frequent resizing
     * while allowing shrinking for memory efficiency.
     * 
     * Optimized for GraphicsContextPool reuse (50px tolerance).
     */
    private void updateViewPortWidth(boolean render) {
        int clamped = (int) Math.max(m_minWidth, Math.min(m_preferredWidth, m_maxWidth));
        int prevWidth = m_viewportWidth;
        
        // Round clamped size to pool tolerance first
        int roundedClamped = ((clamped + 25) / 50) * 50;
        // Calculate target viewport size with buffer
        int targetViewport = roundedClamped + VIEWPORT_BUFFER;
        
        // Grow immediately if we need more space
        if (clamped > m_viewportWidth - VIEWPORT_BUFFER) {
            m_viewportWidth = targetViewport;
        }
        // Shrink only if we have significant excess (2x buffer = 200px)
        // This provides hysteresis to prevent oscillation
        else if (targetViewport < m_viewportWidth - (2 * VIEWPORT_BUFFER)) {
            m_viewportWidth = targetViewport;
        }
  
        // Only re-render if dimensions actually changed
        if (render && prevWidth != m_viewportWidth) {
            requestRender();
        }
    }

    public void setPreferredSize(double width, double height) {
        setPreferredSize(width, height, true);
    }

    public void setPreferredSize(double width, double height, boolean render) {
        setPrefWidth(width, false);
        setPrefHeight(height, render);
    }

    public void setMinWidth(double minWidth) {
        setMinWidth(minWidth, true);
    }

    public void setMinWidth(double minWidth, boolean render) {
        m_minWidth = (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.ceil(minWidth)));
        updateViewPortWidth(render);
    }

    public void setMaxWidth(double maxWidth){
        setMaxWidth(maxWidth, true);
    }
    
    public void setMaxWidth(double maxWidth, boolean render) {
        m_maxWidth = (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.ceil(maxWidth)));
        updateViewPortWidth(render);
    }
    public void setMaxHeight(double maxHeight){
        setMaxHeight(maxHeight, true);
    }
    public void setMaxHeight(double maxHeight, boolean render) {
        m_maxHeight = (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.ceil(maxHeight)));
        updateViewPortHeight(render);
    }

    public void setMinHeight(double minHeight){
        setMinHeight(minHeight, true);
    }

    public void setMinHeight(double minHeight, boolean render) {
        m_minHeight = (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.ceil(minHeight)));
        updateViewPortHeight(render);
    }
    
    public void setSizeMode(SizeMode mode) {
        setSizeMode(mode, true);
    }

    public void setSizeMode(SizeMode mode, boolean render) {
        m_sizeMode = mode;
        setupLayoutListeners();
        updateViewPortWidth(false);
        updateViewPortHeight(render);
    }
    

    
    public int getPreferredWidth() {
        return m_preferredWidth;
    }
    
    public int getPreferredHeight() {
        return m_preferredHeight;
    }
    
    public SizeMode getSizeMode() {
        return m_sizeMode;
    }
    
    public void setFont(Font font) {
        m_font = font;
        invalidateRenderCache();
        if(!isVgrow() && m_sizeMode != SizeMode.FIXED){
            updateHeightFromFont();
        }
        requestRender();
    }
    
    public void setTextColor(Color color) {
        m_textColor = color;
        requestRender();
    }
    
    public void setTextPosition(TextAlignment alignment) {
        m_textAlignment = alignment;
        m_viewportOffset = 0;
        m_scrollOffset = 0;
        invalidateRenderCache();
        requestRender();
    }
    
    public void setPlaceholderText(String text) {
        m_placeholderText = text;
        requestRender();
    }
    
    public void setPlaceholderTextColor(Color color) {
        m_placeholderTextColor = color;
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
    
    public void setCursorColor(Color color) {
        m_cursorColor = color;
        requestRender();
    }
    
    public void setValidator(TextValidator validator) {
        m_validator = validator;
    }
    
    public void setInputMask(InputMask mask) {
        m_inputMask = mask;
    }
    
    public TextAlignment getTextAlignment() {
        return m_textAlignment;
    }
    
    public Font getFont() {
        return m_font;
    }
    
    // ========== Validators (Static Helpers) ==========
    
    /**
     * Create a numeric-only validator
     */
    public static TextValidator numericValidator() {
        return text -> text.matches("[0-9]*");
    }
    
    /**
     * Create a decimal validator using InputHelpers
     */
    public static TextValidator decimalValidator(int maxDecimals) {
        return text -> {
            if (text.isEmpty()) return true;
            
            // Allow partial input like ".", "0.", etc.
            if (text.equals(".") || text.endsWith(".")) {
                return text.chars().filter(ch -> ch == '.').count() == 1;
            }
            
            String formatted = InputHelpers.formatStringToNumber(text, maxDecimals);
            return formatted.equals(text);
        };
    }
    
    /**
     * Create a max length validator
     */
    public static TextValidator maxLengthValidator(int maxLength) {
        return text -> text.length() <= maxLength;
    }
    
    /**
     * Create a regex pattern validator
     */
    public static TextValidator patternValidator(String regex) {
        return text -> text.matches(regex);
    }
    
    /**
     * Combine multiple validators
     */
    public static TextValidator combineValidators(TextValidator... validators) {
        return text -> {
            for (TextValidator validator : validators) {
                if (!validator.validate(text)) {
                    return false;
                }
            }
            return true;
        };
    }
    
    // ========== Cleanup ==========
    
    public void shutdown() {
        // Stop cursor timeline
        if (m_cursorTimeline != null) {
            m_cursorTimeline.stop();
        }
        
        invalidateRenderCache();
        
        // Clear references
        m_text = null;
        m_validator = null;
        m_inputMask = null;
        
        super.shutdown();
    }
}