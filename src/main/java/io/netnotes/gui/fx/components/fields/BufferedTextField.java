package io.netnotes.gui.fx.components.fields;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.gui.fx.components.images.BufferedCanvasView;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.InputHelpers;
import io.netnotes.gui.fx.display.FontMetricsCache;

import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.function.Function;

/**
 * Custom text field with direct rendering using BufferedCanvasView.
 * Features viewport scrolling, text selection, formatting, and validation.
 */
public class BufferedTextField extends BufferedCanvasView {
    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 40;
    
    public enum TextPosition {
        LEFT, CENTER, RIGHT
    }
    
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
    private TextPosition m_textPosition = TextPosition.LEFT;
    private Insets m_insets = new Insets(0);
    private int m_margin = 20;
   
    // Placeholder
    private String m_placeholderText = "";
    private Color m_placeholderTextColor = new Color(150, 150, 150); // Gray
    
    // Dimensions
    private int m_fieldWidth = DEFAULT_WIDTH;
    private int m_fieldHeight = DEFAULT_HEIGHT;
    
    // Validation and formatting
    private TextValidator m_validator = null;
    private InputMask m_inputMask = null;
    
    // Cached font metrics (from global cache)
    private FontMetrics m_cachedFontMetrics = null;
    
    // ========== Functional Interfaces ==========
    
    @FunctionalInterface
    public interface TextValidator {
        boolean validate(String text);
    }
    
    /**
     * Input mask for formatted text entry (like currency, dates, etc.)
     */
    public static class InputMask {
        private String m_pattern;
        private DecimalFormat m_decimalFormat;
        private int m_maxLength = -1;
        private int m_decimalPlaces = -1;
        private Function<String, String> m_formatter;
        
        private InputMask(String pattern) {
            m_pattern = pattern;
        }
        
        public static InputMask currency(int maxDigitsBeforeDecimal, int decimalPlaces) {
            InputMask mask = new InputMask("currency");
            StringBuilder pattern = new StringBuilder("$");
            for (int i = 0; i < maxDigitsBeforeDecimal; i++) {
                pattern.append("#");
            }
            if (decimalPlaces > 0) {
                pattern.append(".");
                for (int i = 0; i < decimalPlaces; i++) {
                    pattern.append("0");
                }
            }
            mask.m_decimalFormat = new DecimalFormat(pattern.toString());
            mask.m_maxLength = maxDigitsBeforeDecimal + (decimalPlaces > 0 ? decimalPlaces + 1 : 0);
            mask.m_decimalPlaces = decimalPlaces;
            return mask;
        }
        
        public static InputMask decimal(int maxDigitsBeforeDecimal, int decimalPlaces) {
            InputMask mask = new InputMask("decimal");
            StringBuilder pattern = new StringBuilder();
            for (int i = 0; i < maxDigitsBeforeDecimal; i++) {
                pattern.append("#");
            }
            if (decimalPlaces > 0) {
                pattern.append(".");
                for (int i = 0; i < decimalPlaces; i++) {
                    pattern.append("0");
                }
            }
            mask.m_decimalFormat = new DecimalFormat(pattern.toString());
            mask.m_maxLength = maxDigitsBeforeDecimal + (decimalPlaces > 0 ? decimalPlaces + 1 : 0);
            mask.m_decimalPlaces = decimalPlaces;
            return mask;
        }
        
        public static InputMask custom(Function<String, String> formatter, int maxLength) {
            InputMask mask = new InputMask("custom");
            mask.m_formatter = formatter;
            mask.m_maxLength = maxLength;
            return mask;
        }
        
        public String format(String input) {
            if (m_formatter != null) {
                return m_formatter.apply(input);
            }
            
            if (m_decimalFormat != null) {
                // Use InputHelpers for proper number formatting
                String cleaned = InputHelpers.formatStringToNumber(input, 
                    m_decimalPlaces >= 0 ? m_decimalPlaces : 10);
                
                if (cleaned.isEmpty() || InputHelpers.isTextZero(cleaned)) {
                    return "";
                }
                
                try {
                    BigDecimal value = new BigDecimal(cleaned);
                    return m_decimalFormat.format(value);
                } catch (NumberFormatException e) {
                    return input;
                }
            }
            
            return input;
        }
        
        public boolean isValidInput(String input) {
            if (m_maxLength > 0 && input.length() > m_maxLength) {
                return false;
            }
            
            if (m_decimalFormat != null) {
                // Allow digits, decimal point, and handle partial input
                return input.matches("[\\d.]*");
            }
            
            return true;
        }
    }
    
    // ========== Constructors ==========
    
    public BufferedTextField() {
        super();
        setRenderMode(RenderMode.GENERATE);
        setFocusTraversable(true);
        
        setupCursorBlink();
        setupEventHandlers();
        requestRender();
    }
    
    public BufferedTextField(int width, int height) {
        this();
        m_fieldWidth = width;
        m_fieldHeight = height;
        requestRender();
    }

    public Insets getInsets(){
        return m_insets;
    }

    public void setInsets(Insets insets){
        m_insets = insets;
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
        return m_fieldWidth;
    }
    
    @Override
    protected int getGeneratedHeight() {
        return m_fieldHeight;
    }
    
    @Override
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Cache font metrics from global cache
        if (m_cachedFontMetrics == null) {
            m_cachedFontMetrics = FontMetricsCache.getInstance().getMetrics(m_font);
        }
        
        // Update viewport to keep cursor visible
        updateViewport();
        
        // Background is transparent - let CSS handle it
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Set font
        g2d.setFont(m_font);
        
        // Get padding from CSS insets
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int paddingTop = (int) insets.getTop();
        int paddingBottom = (int) insets.getBottom();
        
        int availableWidth = width - paddingLeft - paddingRight;
        int availableHeight = height - paddingTop - paddingBottom;
        
        int textY = paddingTop + (availableHeight + m_cachedFontMetrics.getAscent() - 
                     m_cachedFontMetrics.getDescent()) / 2;
        
        // Draw placeholder or text
        if (m_text.isEmpty() && !m_isFocused && !m_placeholderText.isEmpty()) {
            drawPlaceholder(g2d, textY, paddingLeft, availableWidth);
        } else if (!m_text.isEmpty()) {
            drawTextWithSelection(g2d, textY, paddingLeft, availableWidth, paddingTop, availableHeight);
        }
        
        // Draw cursor if focused and visible (but not when selecting)
        if (m_isFocused && m_cursorVisible && !hasSelection() && isCursorInViewport()) {
            drawCursor(g2d, textY, paddingLeft, availableWidth, paddingTop, availableHeight);
        }
    }
    
    private void drawPlaceholder(Graphics2D g2d, int textY, int paddingLeft, int availableWidth) {
        g2d.setColor(m_placeholderTextColor);
        int x = getTextX(m_placeholderText, paddingLeft, availableWidth);
        g2d.drawString(m_placeholderText, x, textY);
    }
    
    private void drawTextWithSelection(Graphics2D g2d, int textY, int paddingLeft, 
                                       int availableWidth, int paddingTop, int availableHeight) {
        String visibleText = getVisibleText();
        int textX = getTextX(visibleText, paddingLeft, availableWidth);
        
        // Set clipping to prevent drawing outside available area
        Shape oldClip = g2d.getClip();
        g2d.setClip(paddingLeft, paddingTop, availableWidth, availableHeight);
        
        if (hasSelection() && isSelectionInViewport()) {
            // Draw text in three parts: before selection, selection, after selection
            int visibleStart = m_viewportOffset;
            int visibleEnd = visibleStart + visibleText.codePointCount(0, visibleText.length());
            
            int selStart = Math.max(m_selectionStart, visibleStart);
            int selEnd = Math.min(m_selectionEnd, visibleEnd);
            
            // Text before selection
            if (selStart > visibleStart) {
                String beforeSel = m_text.substring(visibleStart, selStart).toString();
                g2d.setColor(m_textColor);
                g2d.drawString(beforeSel, textX, textY);
                textX += getStringWidth(beforeSel);
            }
            
            // Selection
            String selectedText = m_text.substring(selStart, selEnd).toString();
            int selectionWidth = getStringWidth(selectedText);
            
            // Draw selection background
            g2d.setColor(m_selectionColor);
            g2d.fillRect(textX, paddingTop, selectionWidth, availableHeight);
            
            // Draw selected text
            g2d.setColor(m_selectedTextColor);
            g2d.drawString(selectedText, textX, textY);
            textX += selectionWidth;
            
            // Text after selection
            if (selEnd < visibleEnd) {
                String afterSel = m_text.substring(selEnd, visibleEnd).toString();
                g2d.setColor(m_textColor);
                g2d.drawString(afterSel, textX, textY);
            }
        } else {
            // No selection, draw normally
            g2d.setColor(m_textColor);
            g2d.drawString(visibleText, textX, textY);
        }
        
        // Restore original clip
        g2d.setClip(oldClip);
    }
    
    private void drawCursor(Graphics2D g2d, int textY, int paddingLeft, int availableWidth,
                           int paddingTop, int availableHeight) {
        int visualCursorPos = m_cursorPosition - m_viewportOffset;
        String textBeforeCursor = m_text.substring(
            m_viewportOffset, m_viewportOffset + visualCursorPos).toString();
        
        String visibleText = getVisibleText();
        int baseX = getTextX(visibleText, paddingLeft, availableWidth);
        int cursorX = baseX + getStringWidth(textBeforeCursor);
        
        g2d.setColor(m_cursorColor);
        g2d.fillRect(cursorX, paddingTop, 2, availableHeight);
    }
    
    /**
     * Get X position based on text alignment
     */
    private int getTextX(String text, int paddingLeft, int availableWidth) {
        int textWidth = getStringWidth(text);
        
        switch (m_textPosition) {
            case CENTER:
                return paddingLeft + Math.max(0, (availableWidth - textWidth) / 2) - m_scrollOffset;
            case RIGHT:
                return paddingLeft + Math.max(0, availableWidth - textWidth) - m_scrollOffset;
            case LEFT:
            default:
                return paddingLeft;
        }
    }
    
    // ========== String Width Calculation ==========
    
    /**
     * Get string width using global font metrics cache.
     * Properly handles surrogate pairs (emoji outside BMP).
     */
    private int getStringWidth(String str) {
        return FontMetricsCache.getInstance().getStringWidth(m_font, str);
    }
    
    /**
     * Get precise string width for variable-width fonts
     */
    private double getStringWidthPrecise(String str) {
        return FontMetricsCache.getInstance().getStringWidthPrecise(m_font, str);
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
        }
    }
    
    // ========== Viewport Management ==========
    
    private void updateViewport() {
        if (m_text.length() == 0) {
            m_viewportOffset = 0;
            m_scrollOffset = 0;
            return;
        }
        
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_fieldWidth - paddingLeft - paddingRight;
        
        // LEFT alignment - traditional scrolling
        if (m_textPosition == TextPosition.LEFT) {
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
                int textWidth = getStringWidth(textToCursor);
                
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
            int fullWidth = getStringWidth(fullText);
            
            // If text fits, center/right align naturally (no scroll needed)
            if (fullWidth <= availableWidth) {
                m_scrollOffset = 0;
                return;
            }
            
            // Calculate cursor position in pixels
            String textBeforeCursor = m_text.substring(0, m_cursorPosition).toString();
            int cursorPixelPos = getStringWidth(textBeforeCursor);
            
            // Calculate where cursor would appear with current scroll
            int displayPos = 0;
            if (m_textPosition == TextPosition.CENTER) {
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
        int availableWidth = m_fieldWidth - paddingLeft - paddingRight;
        
        // For CENTER/RIGHT with scrolling, show all text
        if (m_textPosition != TextPosition.LEFT && m_scrollOffset > 0) {
            return m_text.toString();
        }
        
        // For LEFT or CENTER/RIGHT without scrolling
        if (m_textPosition != TextPosition.LEFT) {
            String fullText = m_text.toString();
            int fullWidth = getStringWidth(fullText);
            
            if (fullWidth <= availableWidth) {
                return fullText;
            }
        }
        
        int endPos = m_viewportOffset;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = getStringWidth(chunk);
            
            if (width > availableWidth) {
                break;
            }
            endPos++;
        }
        
        return m_text.substring(m_viewportOffset, endPos).toString();
    }
    
    private boolean isCursorInViewport() {
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_fieldWidth - paddingLeft - paddingRight;
        
        int endPos = m_viewportOffset;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = getStringWidth(chunk);
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
        
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        double x = event.getX() - paddingLeft;
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
            Insets insets = getInsets();
            int paddingLeft = (int) insets.getLeft();
            double x = event.getX() - paddingLeft;
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
        
        requestRender();
    }
    
    private void applyInputMask() {
        String original = m_text.toString();
        String formatted = m_inputMask.format(original);
        
        if (!formatted.equals(original)) {
            // Track cursor position relative to content changes
            // Get the text before cursor in original
            String beforeCursorOriginal = m_text.substring(0, 
                Math.min(m_cursorPosition, original.length())).toString();
            
            // Count significant characters (digits) before cursor
            int digitsBeforeCursor = 0;
            for (int i = 0; i < beforeCursorOriginal.length(); i++) {
                char c = beforeCursorOriginal.charAt(i);
                if (Character.isDigit(c)) {
                    digitsBeforeCursor++;
                }
            }
            
            // Update text
            m_text = new NoteIntegerArray(formatted);
            
            // Find new cursor position by counting same number of digits
            int newCursorPos = 0;
            int digitsFound = 0;
            for (int i = 0; i < formatted.length() && digitsFound < digitsBeforeCursor; i++) {
                char c = formatted.charAt(i);
                if (Character.isDigit(c)) {
                    digitsFound++;
                }
                newCursorPos = i + 1;
            }
            
            m_cursorPosition = Math.min(newCursorPos, m_text.length());
        }
    }
    
    private void deleteBeforeCursor() {
        if (m_cursorPosition > 0) {
            m_text.deleteCodePointAt(m_cursorPosition - 1);
            m_cursorPosition--;
            requestRender();
        }
    }
    
    private void deleteAfterCursor() {
        if (m_cursorPosition < m_text.length()) {
            m_text.deleteCodePointAt(m_cursorPosition);
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
    
    private int estimateCursorPosition(double x) {
        if (m_cachedFontMetrics == null) {
            return m_viewportOffset;
        }
        
        Insets insets = getInsets();
        int paddingLeft = (int) insets.getLeft();
        int paddingRight = (int) insets.getRight();
        int availableWidth = m_fieldWidth - paddingLeft - paddingRight;
        
        String visibleText = getVisibleText();
        int baseX = getTextX(visibleText, paddingLeft, availableWidth);
        double relativeX = x - baseX;
        
        int closestPos = m_viewportOffset;
        double closestDist = Math.abs(relativeX);
        
        int visibleLength = visibleText.codePointCount(0, visibleText.length());
        
        // Use getStringBounds for better precision with variable-width fonts
        for (int i = 0; i <= visibleLength; i++) {
            int absolutePos = m_viewportOffset + i;
            if (absolutePos > m_text.length()) break;
            
            String textBeforePos = m_text.substring(
                m_viewportOffset, absolutePos).toString();
            
            // Use precise measurement for cursor positioning
            double textWidth = textBeforePos.isEmpty() ? 0 : 
                getStringWidthPrecise(textBeforePos);
            
            double dist = Math.abs(relativeX - textWidth);
            
            if (dist < closestDist) {
                closestDist = dist;
                closestPos = absolutePos;
            }
        }
        
        return closestPos;
    }
    
    // ========== Public API ==========
    
    public String getText() {
        return m_text.toString();
    }
    
    public void setText(String newText) {
        m_text = new NoteIntegerArray(newText);
        m_cursorPosition = m_text.length();
        m_viewportOffset = 0;
        m_scrollOffset = 0;
        clearSelection();
        requestRender();
    }
    
    public void clear() {
        m_text.clear();
        m_cursorPosition = 0;
        m_viewportOffset = 0;
        m_scrollOffset = 0;
        clearSelection();
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
    
    public void setFieldWidth(int width) {
        m_fieldWidth = width;
        requestRender();
    }
    
    public void setFieldHeight(int height) {
        m_fieldHeight = height;
        requestRender();
    }
    
    public void setFont(Font font) {
        m_font = font;
        m_cachedFontMetrics = null; // Will be refreshed from global cache on next render
        requestRender();
    }
    
    public void setTextColor(Color color) {
        m_textColor = color;
        requestRender();
    }
    
    public void setTextPosition(TextPosition position) {
        m_textPosition = position;
        m_viewportOffset = 0;
        m_scrollOffset = 0;
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
    
    public TextPosition getTextPosition() {
        return m_textPosition;
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
    
    @Override
    public void shutdown() {
        // Stop cursor timeline
        if (m_cursorTimeline != null) {
            m_cursorTimeline.stop();
            m_cursorTimeline = null;
        }
        
        // Clear cached references (global cache handles actual font metrics)
        m_cachedFontMetrics = null;
        
        // Clear references
        m_text = null;
        m_validator = null;
        m_inputMask = null;
        
        super.shutdown();
    }
}