package io.netnotes.gui.fx.components.fields;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.gui.fx.components.images.BufferedCanvasView;
import io.netnotes.gui.fx.display.FxResourceFactory;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.function.Function;

/**
 * Custom text field with direct rendering using BufferedCanvasView.
 * Features viewport scrolling, text selection, formatting, and validation.
 */
public class BufferedTextField extends BufferedCanvasView {
    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 40;
    private static final int PADDING = 8;
    
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
    
    // Placeholder
    private String m_placeholderText = "";
    private Color m_placeholderTextColor = new Color(150, 150, 150); // Gray
    
    // Dimensions
    private int m_fieldWidth = DEFAULT_WIDTH;
    private int m_fieldHeight = DEFAULT_HEIGHT;
    
    // Validation and formatting
    private TextValidator m_validator = null;
    private InputMask m_inputMask = null;
    
    // Cached font metrics (reused for performance)
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
                // Remove non-digit characters except decimal point
                String cleaned = input.replaceAll("[^\\d.]", "");
                if (cleaned.isEmpty()) {
                    return "";
                }
                try {
                    double value = Double.parseDouble(cleaned);
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
                // Allow digits and decimal point
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
        // Cache font metrics if not already cached
        if (m_cachedFontMetrics == null) {
            g2d.setFont(m_font);
            m_cachedFontMetrics = g2d.getFontMetrics();
        }
        
        // Update viewport to keep cursor visible
        updateViewport();
        
        // Background is transparent - let CSS handle it
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Set font
        g2d.setFont(m_font);
        
        int textY = (height + m_cachedFontMetrics.getAscent() - 
                     m_cachedFontMetrics.getDescent()) / 2;
        
        // Draw placeholder or text
        if (m_text.isEmpty() && !m_isFocused && !m_placeholderText.isEmpty()) {
            drawPlaceholder(g2d, textY, width);
        } else if (!m_text.isEmpty()) {
            drawTextWithSelection(g2d, textY, width);
        }
        
        // Draw cursor if focused and visible
        if (m_isFocused && m_cursorVisible && isCursorInViewport() && m_selectionStart == -1) {
            drawCursor(g2d, textY, width, height);
        }
    }
    
    private void drawPlaceholder(Graphics2D g2d, int textY, int width) {
        g2d.setColor(m_placeholderTextColor);
        int x = getTextX(m_placeholderText, width);
        g2d.drawString(m_placeholderText, x, textY);
    }
    
    private void drawTextWithSelection(Graphics2D g2d, int textY, int width) {
        String visibleText = getVisibleText();
        int textX = getTextX(visibleText, width);
        
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
                textX += m_cachedFontMetrics.stringWidth(beforeSel);
            }
            
            // Selection
            String selectedText = m_text.substring(selStart, selEnd).toString();
            int selectionWidth = m_cachedFontMetrics.stringWidth(selectedText);
            
            // Draw selection background
            g2d.setColor(m_selectionColor);
            g2d.fillRect(textX, PADDING, selectionWidth, m_fieldHeight - PADDING * 2);
            
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
    }
    
    private void drawCursor(Graphics2D g2d, int textY, int width, int height) {
        int visualCursorPos = m_cursorPosition - m_viewportOffset;
        String textBeforeCursor = m_text.substring(
            m_viewportOffset, m_viewportOffset + visualCursorPos).toString();
        
        String visibleText = getVisibleText();
        int baseX = getTextX(visibleText, width);
        int cursorX = baseX + m_cachedFontMetrics.stringWidth(textBeforeCursor);
        
        g2d.setColor(m_cursorColor);
        g2d.fillRect(cursorX, PADDING, 2, height - PADDING * 2);
    }
    
    /**
     * Get X position based on text alignment
     */
    private int getTextX(String text, int width) {
        int textWidth = m_cachedFontMetrics.stringWidth(text);
        int availableWidth = width - (PADDING * 2);
        
        switch (m_textPosition) {
            case CENTER:
                return PADDING + Math.max(0, (availableWidth - textWidth) / 2);
            case RIGHT:
                return PADDING + Math.max(0, availableWidth - textWidth);
            case LEFT:
            default:
                return PADDING;
        }
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
            return;
        }
        
        // For center/right alignment, we need different viewport logic
        if (m_textPosition != TextPosition.LEFT) {
            m_viewportOffset = 0; // Show all text for center/right
            return;
        }
        
        int availableWidth = m_fieldWidth - (PADDING * 2);
        
        // If cursor is before viewport, scroll left
        if (m_cursorPosition < m_viewportOffset) {
            m_viewportOffset = m_cursorPosition;
            return;
        }
        
        // Check if cursor is beyond visible area, scroll right
        while (m_cursorPosition > m_viewportOffset) {
            String textToCursor = m_text.substring(
                m_viewportOffset, m_cursorPosition).toString();
            int textWidth = m_cachedFontMetrics.stringWidth(textToCursor);
            
            if (textWidth <= availableWidth - 4) {
                break;
            }
            
            m_viewportOffset++;
        }
    }
    
    private String getVisibleText() {
        if (m_text.length() == 0) {
            return "";
        }
        
        // For center/right alignment with short text, show everything
        if (m_textPosition != TextPosition.LEFT) {
            String fullText = m_text.toString();
            int fullWidth = m_cachedFontMetrics.stringWidth(fullText);
            int availableWidth = m_fieldWidth - (PADDING * 2);
            
            if (fullWidth <= availableWidth) {
                return fullText;
            }
        }
        
        int availableWidth = m_fieldWidth - (PADDING * 2);
        int endPos = m_viewportOffset;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = m_cachedFontMetrics.stringWidth(chunk);
            
            if (width > availableWidth) {
                break;
            }
            endPos++;
        }
        
        return m_text.substring(m_viewportOffset, endPos).toString();
    }
    
    private boolean isCursorInViewport() {
        int availableWidth = m_fieldWidth - (PADDING * 2);
        int endPos = m_viewportOffset;
        
        while (endPos < m_text.length()) {
            String chunk = m_text.substring(m_viewportOffset, endPos + 1).toString();
            int width = m_cachedFontMetrics.stringWidth(chunk);
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
        
        double x = event.getX() - PADDING;
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
            double x = event.getX() - PADDING;
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
        // Validate input
        String testText = m_text.substring(0, m_cursorPosition).toString() 
                        + str 
                        + m_text.substring(m_cursorPosition).toString();
        
        if (m_validator != null && !m_validator.validate(testText)) {
            return;
        }
        
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
        String formatted = m_inputMask.format(m_text.toString());
        if (!formatted.equals(m_text.toString())) {
            int oldCursor = m_cursorPosition;
            m_text = new NoteIntegerArray(formatted);
            m_cursorPosition = Math.min(oldCursor, m_text.length());
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
        
        String visibleText = getVisibleText();
        int baseX = getTextX(visibleText, m_fieldWidth);
        double relativeX = x - baseX;
        
        int closestPos = m_viewportOffset;
        double closestDist = Math.abs(relativeX);
        
        int visibleLength = visibleText.codePointCount(0, visibleText.length());
        
        for (int i = 0; i <= visibleLength; i++) {
            int absolutePos = m_viewportOffset + i;
            if (absolutePos > m_text.length()) break;
            
            String textBeforePos = m_text.substring(
                m_viewportOffset, absolutePos).toString();
            double textWidth = m_cachedFontMetrics.stringWidth(textBeforePos);
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
        clearSelection();
        requestRender();
    }
    
    public void clear() {
        m_text.clear();
        m_cursorPosition = 0;
        m_viewportOffset = 0;
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
        m_cachedFontMetrics = null;
        requestRender();
    }
    
    public void setTextColor(Color color) {
        m_textColor = color;
        requestRender();
    }
    
    public void setTextPosition(TextPosition position) {
        m_textPosition = position;
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
    
    public void setValidator(TextValidator validator) {
        m_validator = validator;
    }
    
    public void setInputMask(InputMask mask) {
        m_inputMask = mask;
    }
    
    // ========== Cleanup ==========
    
    @Override
    public void shutdown() {
        if (m_cursorTimeline != null) {
            m_cursorTimeline.stop();
        }
        m_cachedFontMetrics = null;
        super.shutdown();
    }
}