package io.netnotes.gui.fx.components.layout;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.engine.utils.MathHelpers;

import io.netnotes.gui.fx.components.canvas.BufferedCanvasView;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.GraphicsContextPool;
import io.netnotes.gui.fx.display.TextRenderer;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.noteBytes.NoteBytesImage;
import io.netnotes.gui.fx.utils.TaskUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * COMPLETED IMPLEMENTATION
 * A unified layout/editing component that can function as both:
 * 1. UI Layout Tool - Positioning and displaying UI elements
 * 2. Text Editor - Editing text with cursor traversal and selection
 */
public class LayoutCanvas extends BufferedCanvasView {
    private static final int DEFAULT_WIDTH = 600;
    private static final int DEFAULT_HEIGHT = 400;
    private static final int VIEWPORT_BUFFER = GraphicsContextPool.SIZE_TOLERANCE;
    //  private static final int SCROLL_SPEED = 20;
    private static final int VIRTUAL_SCROLL_MARGIN = 200;

    
    private HostServices m_hostServices;

    // ========== Render Queueing =======

    private final AtomicReference<Runnable> pendingDragEvent = new AtomicReference<>(null);
    private final AtomicBoolean isDragProcessing = new AtomicBoolean(false);
    
    // ========== Data Storage ==========

    private NoteBytesArray m_segments;
    private LayoutEngine m_layoutEngine;
    private CursorSelectionSystem.CursorNavigator m_navigator;

    private final Map<String, LayoutEngine.LayoutResult> m_layoutCache;
    // ========== Layout State ==========
    
    private LayoutEngine.LayoutResult m_layoutResult;
    private boolean m_layoutDirty;

    // ========== Cursor & Selection ==========
    
    private CursorSelectionSystem.CursorPosition m_cursor;
    private CursorSelectionSystem.Selection m_selection;
    private boolean m_isSelecting;
    
    // ========== Visual State ==========
    
    private boolean m_cursorVisible;
    private boolean m_isFocused;
    private Timeline m_cursorTimeline;

    // ========== Layer system ==========
    private BufferedImage m_contentLayer = null;
    private boolean m_contentDirty = true;
    
    // ========== Scrolling ==========
    
    private int m_scrollX;
    private int m_scrollY;
    private int m_maxScrollX;
    private int m_maxScrollY;
    
    // ========== Styling ==========
    
    private Color m_backgroundColor;
    private Color m_cursorColor;
    private Color m_selectionColor;
    private Insets m_insets;
    
    // ========== Dimensions ==========
    
    private int m_preferredWidth;
    private int m_preferredHeight;
    private int m_viewportWidth;
    private int m_viewportHeight;
    
    // ========== Text Renderer ==========
    
    private final TextRenderer m_textRenderer = TextRenderer.getInstance();
    
    // ========== Layout Listeners ==========
    
    private ChangeListener<Number> widthListener;
    private ChangeListener<Number> heightListener;
    
    // ========== Constructors ==========
    
    public LayoutCanvas() {
        this(null, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public LayoutCanvas(int width, int height) {
        this(null, width, height);
    }
    
    public LayoutCanvas(HostServices hostServices, int width, int height) {
        super();
        this.m_hostServices = hostServices;
        m_layoutCache = new HashMap<>();
        // Enable real-time coalescing mode - keeps only latest render request
        isRealTimeTask().set(true);
        
        m_preferredWidth = width;
        m_preferredHeight = height;
        m_viewportWidth = width + VIEWPORT_BUFFER;
        m_viewportHeight = height + VIEWPORT_BUFFER;
        
        

        m_segments = new NoteBytesArray();
        m_layoutEngine = new LayoutEngine();
        
        m_cursor = new CursorSelectionSystem.CursorPosition();
        m_selection = null;
        
        m_scrollX = 0;
        m_scrollY = 0;
        
        m_backgroundColor = Color.WHITE;
        m_cursorColor = new Color(59, 130, 246);
        m_selectionColor = new Color(59, 130, 246, 80);
        m_insets = new Insets(10, 10, 10, 10);

      
        setRenderMode(RenderMode.GENERATE);
        setFocusTraversable(true);

        setupEventHandlers();
        setupLayoutListeners();
        rebuildNavigator();
        setupCursorBlink();

        // Initial layout
        invalidateLayout();
    }
    
    // ========== BufferedCanvasView Implementation ==========
    
    public void applyLayout(){
        if (m_layoutDirty) {
            computeLayout();
            m_layoutDirty = false;
            m_contentDirty = true;
            requestRender(); 
        }
    }

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
    
    // Handle resize events with coalescing
    private void setupLayoutListeners() {
        widthListener = (_, _, newVal) -> {
            int val = (int) Math.ceil(newVal.doubleValue());
            if (val > 0 && val != m_preferredWidth) {
                m_preferredWidth = val;
                m_viewportWidth = val + VIEWPORT_BUFFER;
                invalidateLayout(); 
            }
        };
        widthProperty().addListener(widthListener);
        
        heightListener = (_, _, newVal) -> {
            int val = (int) Math.ceil(newVal.doubleValue());
            if (val > 0 && val != m_preferredHeight) {
                m_preferredHeight = val;
                m_viewportHeight = val + VIEWPORT_BUFFER;
                invalidateLayout(); 
            }
        };
        heightProperty().addListener(heightListener);
    }
    
    @Override
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Regenerate content layer if dirty
        if (m_contentDirty || m_contentLayer == null || 
            m_contentLayer.getWidth() != width || m_contentLayer.getHeight() != height) {
            
            if (m_contentLayer == null || 
                m_contentLayer.getWidth() != width || 
                m_contentLayer.getHeight() != height) {
                m_contentLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            }
            
            Graphics2D contentG2d = m_contentLayer.createGraphics();
            contentG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                       RenderingHints.VALUE_ANTIALIAS_ON);
            contentG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
                                       RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            drawContentLayer(contentG2d, width, height);
            contentG2d.dispose();
            m_contentDirty = false;
        }
        
        // Composite layers
        g2d.drawImage(m_contentLayer, 0, 0, null);
        drawOverlayLayer(g2d, width, height);
    }

    /**
     * Render layout result with virtual scrolling optimization.
     * Skips segments outside visible bounds (viewport + margin).
     * 
     * @param hasSelection - if true, renders selection (for overlay), if false skips selection (for content layer)
     */
    private void renderLayoutResultVirtual(Graphics2D g2d, 
                                          LayoutEngine.LayoutResult result, 
                                          int offsetX, int offsetY,
                                          int visibleTop, int visibleBottom,
                                          int visibleLeft, int visibleRight,
                                          boolean hasSelection,
                                          int selStart, int selEnd) {
        LayoutSegment segment = result.segment;
        Rectangle bounds = result.bounds;
        
        // Skip if display:none
        if (segment.getLayout().display == LayoutSegment.Display.NONE) {
            return;
        }
        
        int x = bounds.x + offsetX;
        int y = bounds.y + offsetY;
        int w = bounds.width;
        int h = bounds.height;
        
        // VIRTUAL SCROLLING: Skip if completely outside visible range
        if (y + h < visibleTop || y > visibleBottom ||
            x + w < visibleLeft || x > visibleRight) {
            return; // Don't render this segment or its children
        }
        
        // Segment is at least partially visible - render it
        
        // Render background
        if (segment.getLayout().backgroundColor != null) {
            g2d.setColor(segment.getLayout().backgroundColor);
            g2d.fillRect(x, y, w, h);
        }
        
        // Render border
        if (segment.getLayout().borderWidth > 0 && segment.getLayout().borderColor != null) {
            g2d.setColor(segment.getLayout().borderColor);
            g2d.setStroke(new BasicStroke(segment.getLayout().borderWidth));
            g2d.drawRect(x, y, w, h);
        }
        
        // Render selection ONLY if hasSelection is true (for overlay layer)
        if (hasSelection) {
            int segStart = result.globalStartOffset;
            int segEnd = result.globalEndOffset;
            boolean isInRange = !(selEnd <= segStart || selStart >= segEnd);
            
            if (isInRange) {
                if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
                    renderTextSelection(g2d, result, offsetX, offsetY, selStart, selEnd);
                } else {
                    g2d.setColor(m_selectionColor);
                    g2d.fillRect(x, y, w, h);
                }
            }
        }
        
        // Render content (skip if display:hidden)
        if (segment.getLayout().display != LayoutSegment.Display.HIDDEN) {
            switch (segment.getType()) {
                case TEXT:
                    renderTextWithWrapping(g2d, segment, bounds, offsetX, offsetY);
                    break;
                    
                case IMAGE:
                    renderImage(g2d, segment, bounds, offsetX, offsetY);
                    break;
                    
                case SPACER:
                    // Nothing to render
                    break;
                    
                default:
                    break;
            }
        }
        
        // Render children with same virtual scrolling bounds
        for (LayoutEngine.LayoutResult child : result.children) {
            renderLayoutResultVirtual(g2d, child, offsetX, offsetY,
                                     visibleTop, visibleBottom, 
                                     visibleLeft, visibleRight,
                                     hasSelection, selStart, selEnd);
        }
    }

   /**
     * Draw static content layer with virtual scrolling optimization.
     * Only renders segments within viewport + margin.
     */
    private void drawContentLayer(Graphics2D g2d, int width, int height) {
        // Clear background
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        g2d.setColor(m_backgroundColor);
        g2d.fillRect(0, 0, width, height);
        
        // Set clipping region
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        int paddingRight = m_insets.right;
        int paddingBottom = m_insets.bottom;
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        int availableHeight = m_preferredHeight - paddingTop - paddingBottom;
        
        g2d.setClip(paddingLeft, paddingTop, availableWidth, availableHeight);
        
        // Calculate virtual scrolling bounds (viewport + margin)
        int visibleTop = m_scrollY - VIRTUAL_SCROLL_MARGIN;
        int visibleBottom = m_scrollY + availableHeight + VIRTUAL_SCROLL_MARGIN;
        int visibleLeft = m_scrollX - VIRTUAL_SCROLL_MARGIN;
        int visibleRight = m_scrollX + availableWidth + VIRTUAL_SCROLL_MARGIN;
        
        // Render layout tree with virtual scrolling
        if (m_layoutResult != null) {
            // No selection in content layer
            renderLayoutResultVirtual(g2d, m_layoutResult, paddingLeft - m_scrollX, paddingTop - m_scrollY,
                visibleTop, visibleBottom, visibleLeft, visibleRight,false, 0, 0); 
        }
        
        g2d.setClip(null);
        m_lastRenderScrollX = m_scrollX;
        m_lastRenderScrollY = m_scrollY;
    }
    

   /**
     * Draw dynamic overlay layer (cursor, selection).
     * This is redrawn every frame but is very fast.
     */
    private void drawOverlayLayer(Graphics2D g2d, int width, int height) {
        // Set clipping region
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        int paddingRight = m_insets.right;
        int paddingBottom = m_insets.bottom;
        int availableWidth = m_preferredWidth - paddingLeft - paddingRight;
        int availableHeight = m_preferredHeight - paddingTop - paddingBottom;
        
        g2d.setClip(paddingLeft, paddingTop, availableWidth, availableHeight);
        
        // Pre-compute selection info once
        boolean hasSelection = m_selection != null;
        CursorSelectionSystem.Selection normalizedSelection = hasSelection ? m_selection.normalized() : null;
        int selStart = hasSelection ? normalizedSelection.getStart().getGlobalOffset() : 0;
        int selEnd = hasSelection ? normalizedSelection.getEnd().getGlobalOffset() : 0;
        
        // Render selection highlights
        if (hasSelection && m_layoutResult != null) {
            renderSelectionOverlay(g2d, m_layoutResult, 
                                  paddingLeft - m_scrollX, 
                                  paddingTop - m_scrollY,
                                  selStart, selEnd);
        }
        
        // Render cursor (only when no selection and focused)
        if (m_isFocused && m_cursorVisible && !hasSelection) {
            renderCursor(g2d, paddingLeft - m_scrollX, paddingTop - m_scrollY);
        }
        
        g2d.setClip(null);
    }
    
     /**
     * Render selection highlights only (for overlay layer).
     * Uses pre-computed selection range for efficiency.
     */
    private void renderSelectionOverlay(Graphics2D g2d, 
                                       LayoutEngine.LayoutResult result, 
                                       int offsetX, int offsetY,
                                       int selStart, int selEnd) {
        LayoutSegment segment = result.segment;
        Rectangle bounds = result.bounds;
        
        // Skip if display:none
        if (segment.getLayout().display == LayoutSegment.Display.NONE) {
            return;
        }
        
        int segStart = result.globalStartOffset;
        int segEnd = result.globalEndOffset;
        
        boolean notInRange = segEnd <= selStart || segStart >= selEnd;
        if (notInRange) {
            // This segment and ALL its children are outside selection range
            return; // Don't recurse to children
        }

        
        // Render selection for this segment
        if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
            renderTextSelection(g2d, result, offsetX, offsetY, selStart, selEnd);
        } else {
            // Non-text segment - highlight entire bounds
            g2d.setColor(m_selectionColor);
            g2d.fillRect(
                bounds.x + offsetX,
                bounds.y + offsetY,
                bounds.width,
                bounds.height
            );
        }
    
        
        // Recurse to children
        for (LayoutEngine.LayoutResult child : result.children) {
            renderSelectionOverlay(g2d, child, offsetX, offsetY, selStart, selEnd);
        }
    }
    
    /**
     * Render text selection highlight (optimized with pre-computed range).
     */
    private void renderTextSelection(Graphics2D g2d, 
                                     LayoutEngine.LayoutResult result,
                                     int offsetX, int offsetY,
                                     int selStart, int selEnd) {
        NoteIntegerArray text = result.segment.getTextContent();
        if (text == null || text.length() == 0) {
            return;
        }
        
        String str = text.toString();
        Font font = result.segment.getStyle().getFont();
        
        int segStart = result.globalStartOffset;
       // int segEnd = result.globalEndOffset;
        
        int localStart = Math.max(0, selStart - segStart);
        int localEnd = Math.min(str.length(), selEnd - segStart);
        
        if (localEnd > localStart) {
            String beforeSel = str.substring(0, localStart);
            String selected = str.substring(localStart, localEnd);
            
            int beforeWidth = m_textRenderer.getTextWidth(beforeSel, font);
            int selWidth = m_textRenderer.getTextWidth(selected, font);
            
            Rectangle bounds = result.bounds;
            int x = bounds.x + offsetX + result.segment.getLayout().padding.left + beforeWidth;
            int y = bounds.y + offsetY + result.segment.getLayout().padding.top;
            int h = bounds.height - result.segment.getLayout().padding.top -
                    result.segment.getLayout().padding.bottom;
            
            g2d.setColor(m_selectionColor);
            g2d.fillRect(x, y, selWidth, h);
        }
    }
    
    // ========== DeferredLayoutManager Integration ==========
    
    /**
     * Mark layout as dirty and schedule deferred layout computation.
     * Replaces: m_layoutDirty = true; requestRender();
     * 
     * Use this for:
     * - Structure changes (add/remove segments)
     * - Content changes (text edit)
     * - Size changes (window resize)
     * - Style changes (fonts, colors that affect layout)
     */
    public void invalidateLayout() {
        m_layoutDirty = true;
        m_layoutCache.clear();
        DeferredLayoutManager.markDirty(this);
    }
    
  
    // ========== Layout Computation ==========

    public static String generateLayoutKey(byte[] bytes, int width, int height){
        return HashServices.digestToUrlSafeString(bytes, 16) + "_" + width + "x" + height;
    }
    
    private void computeLayout() {
        if (m_segments.size() == 0) {
            m_layoutResult = null;
            return;
        }
        
        int availableWidth = m_preferredWidth - m_insets.left - m_insets.right;
        int availableHeight = m_preferredHeight - m_insets.top - m_insets.bottom;
        
        // Simple cache key
        String layoutKey = generateLayoutKey(m_segments.get(), availableWidth, availableHeight);
        
        // Check instance-level cache
        LayoutEngine.LayoutResult cached = m_layoutCache.get(layoutKey);
        if (cached != null) {
            m_layoutResult = cached;
            updateScrollBounds();
            return;
        }
        
        // Compute layout
        LayoutEngine.Constraints constraints = LayoutEngine.Constraints.loose(
            availableWidth, availableHeight
        );
        
        m_layoutResult = m_layoutEngine.layout(m_segments, constraints);
        
        // Cache it (simple, no cross-instance complexity)
        if (m_layoutResult != null) {
            m_layoutCache.put(layoutKey, m_layoutResult);
            updateScrollBounds();
        }
    }


    private void updateScrollBounds() {
        int availableWidth = m_preferredWidth - m_insets.left - m_insets.right;
        int availableHeight = m_preferredHeight - m_insets.top - m_insets.bottom;
        
        m_maxScrollX = Math.max(0, m_layoutResult.bounds.width - availableWidth);
        m_maxScrollY = Math.max(0, m_layoutResult.bounds.height - availableHeight);
        
        m_scrollX = Math.max(0, Math.min(m_scrollX, m_maxScrollX));
        m_scrollY = Math.max(0, Math.min(m_scrollY, m_maxScrollY));
    }
    
    // ========== Rendering ==========
 
    private void renderTextWithWrapping(Graphics2D g2d, LayoutSegment segment, Rectangle bounds, int offsetX, int offsetY) {
        NoteIntegerArray textContent = segment.getTextContent();
        if (textContent == null || textContent.length() == 0) {
            return;
        }
        
        String text = textContent.toString();
        Font font = segment.getStyle().getFont();
        Color color = segment.getStyle().textColor;
        
        g2d.setFont(font);
        g2d.setColor(color);
        
        FontMetrics metrics = m_textRenderer.getMetrics(font);
        int textX = bounds.x + offsetX + segment.getLayout().padding.left;
        int textY = bounds.y + offsetY + segment.getLayout().padding.top + metrics.getAscent();
        
        int availableWidth = bounds.width - segment.getLayout().padding.left - segment.getLayout().padding.right;
        
        // Check if wrapping is needed
        int textWidth = m_textRenderer.getTextWidth(text, font);
        if (textWidth <= availableWidth || segment.getLayout().display == LayoutSegment.Display.INLINE) {
            // Simple single-line rendering
            g2d.drawString(text, textX, textY);
        } else {
            // Word wrap
            wrapAndRenderText(g2d, text, font, metrics, textX, textY, availableWidth);
        }
    }
    
    /**
     * NEW: Helper method to wrap and render text
     */
    private void wrapAndRenderText(Graphics2D g2d, String text, Font font, FontMetrics metrics, int x, int y, int maxWidth) {
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int currentY = y;
        int lineHeight = metrics.getHeight();
        
        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            int testWidth = m_textRenderer.getTextWidth(testLine, font);
            
            if (testWidth > maxWidth && currentLine.length() > 0) {
                // Draw current line and start new one
                g2d.drawString(currentLine.toString(), x, currentY);
                currentLine = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        
        // Draw remaining text
        if (currentLine.length() > 0) {
            g2d.drawString(currentLine.toString(), x, currentY);
        }
    }





   

    private void renderImage(Graphics2D g2d, LayoutSegment segment, Rectangle bounds, 
                        int offsetX, int offsetY) {
    int x = bounds.x + offsetX + segment.getLayout().padding.left;
    int y = bounds.y + offsetY + segment.getLayout().padding.top;
    int width = bounds.width - segment.getLayout().padding.left - segment.getLayout().padding.right;
    int height = bounds.height - segment.getLayout().padding.top - segment.getLayout().padding.bottom;
    
    try {
        // Get scaled image from segment's cache
        BufferedImage scaledImage = segment.getScaledImage(width, height);
        
        if (scaledImage != null) {
            g2d.drawImage(scaledImage, x, y, null);
        } else {
            renderImagePlaceholder(g2d, bounds, offsetX, offsetY, "No image data");
        }
        
    } catch (Exception e) {
        System.err.println("Error rendering image: " + e.getMessage());
        renderImagePlaceholder(g2d, bounds, offsetX, offsetY, "Error: " + e.getMessage());
    }
}

    /**
     * Add an image segment from byte array
     */
    public void addImage(byte[] imageData, int width, int height, boolean render) {
        NoteBytesImage image = new NoteBytesImage(imageData);
        
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.IMAGE);
        segment.setBinaryContent(image);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().width = LayoutSegment.Dimension.px(width);
        segment.getLayout().height = LayoutSegment.Dimension.px(height);
        
        addSegment(segment);
    }

    /**
     * Add an image segment with percentage sizing
     */
    public void addImageWithPercentageSize(byte[] imageData, double widthPercent, boolean render) {
        NoteBytesImage image = new NoteBytesImage(imageData);
        
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.IMAGE);
        segment.setBinaryContent(image);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().width = LayoutSegment.Dimension.percent(widthPercent);
        
        try {
            // Calculate and store aspect ratio
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            BigDecimal aspectRatio = MathHelpers.divideNearestNeighbor(imgWidth, imgHeight);
            
            // Store aspect ratio so layout engine can calculate height
            segment.getLayout().aspectRatio = aspectRatio;
            segment.getLayout().height = LayoutSegment.Dimension.auto();
            
        } catch (IOException e) {
            // If we can't get dimensions, just use auto for both
            segment.getLayout().aspectRatio = null;
            segment.getLayout().height = LayoutSegment.Dimension.auto();
        }
        
        addSegment(segment);
    }

    
    private void renderImagePlaceholder(Graphics2D g2d, Rectangle bounds, int offsetX, int offsetY, String message) {
        int x = bounds.x + offsetX;
        int y = bounds.y + offsetY;
        int width = bounds.width;
        int height = bounds.height;
        
        // Draw gray background
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(x, y, width, height);
        
        // Draw border
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(x, y, width, height);
        
        // Draw message
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2d.getFontMetrics();
        int messageWidth = fm.stringWidth(message);
        int messageHeight = fm.getHeight();
        
        g2d.drawString(message, 
            x + (width - messageWidth) / 2,
            y + (height + messageHeight) / 2 - fm.getDescent()
        );
    }

    /*private boolean isSegmentSelected(LayoutEngine.LayoutResult result) {
        if (m_selection == null) return false;
        
        CursorSelectionSystem.Selection normalized = m_selection.normalized();
        int selStart = normalized.getStart().getGlobalOffset();
        int selEnd = normalized.getEnd().getGlobalOffset();
        
        int segStart = result.globalStartOffset;
        int segEnd = result.globalEndOffset;
        
        // Check for overlap
        return !(selEnd <= segStart || selStart >= segEnd);
    }*/
    
    
    private void renderCursor(Graphics2D g2d, int offsetX, int offsetY) {
        if (m_layoutResult == null) return;
        
        LayoutEngine.LayoutResult result = m_layoutResult.findAtOffset(m_cursor.getGlobalOffset());
        if (result == null) return;
        
        Rectangle bounds = result.bounds;
        LayoutSegment segment = result.segment;
        
        int cursorX = bounds.x + offsetX + segment.getLayout().padding.left;
        int cursorY = bounds.y + offsetY + segment.getLayout().padding.top;
        int cursorH = bounds.height - segment.getLayout().padding.top -
                      segment.getLayout().padding.bottom;
        
        // For text segments, position cursor within text
        if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
            NoteIntegerArray text = segment.getTextContent();
            if (text != null) {
                int localOffset = m_cursor.getLocalOffset();
                String str = text.toString();
                
                if (localOffset > 0 && localOffset <= str.length()) {
                    String beforeCursor = str.substring(0, localOffset);
                    Font font = segment.getStyle().getFont();
                    int beforeWidth = m_textRenderer.getTextWidth(beforeCursor, font);
                    cursorX += beforeWidth;
                }
            }
        }
        
        g2d.setColor(m_cursorColor);
        g2d.fillRect(cursorX, cursorY, 2, cursorH);
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
    
    private void ensureCursorVisible() {
        if (m_layoutResult == null) return;
        
        LayoutEngine.LayoutResult result = m_layoutResult.findAtOffset(m_cursor.getGlobalOffset());
        if (result == null) return;
        
        Rectangle bounds = result.bounds;
        
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        int paddingRight = m_insets.right;
        int paddingBottom = m_insets.bottom;
        
        int viewportWidth = m_preferredWidth - paddingLeft - paddingRight;
        int viewportHeight = m_preferredHeight - paddingTop - paddingBottom;
        
        // Horizontal scrolling
        if (bounds.x < m_scrollX) {
            m_scrollX = bounds.x;
        } else if (bounds.x + bounds.width > m_scrollX + viewportWidth) {
            m_scrollX = bounds.x + bounds.width - viewportWidth;
        }
        
        // Vertical scrolling
        if (bounds.y < m_scrollY) {
            m_scrollY = bounds.y;
        } else if (bounds.y + bounds.height > m_scrollY + viewportHeight) {
            m_scrollY = bounds.y + bounds.height - viewportHeight;
        }
        
        m_scrollX = Math.max(0, Math.min(m_scrollX, m_maxScrollX));
        m_scrollY = Math.max(0, Math.min(m_scrollY, m_maxScrollY));
    }
    
    // ========== Event Handlers ==========
    
   
    private void setupEventHandlers() {

        setOnKeyPressed(this::handleKeyPressed);
        setOnKeyTyped(this::handleKeyTyped);
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        setOnMouseClicked(this::handleMouseClicked);
        setOnScroll(this::handleScroll);
    }
    
    
    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        boolean shift = event.isShiftDown();
        boolean ctrl = event.isControlDown();
        
        m_cursorVisible = true;
        m_cursorTimeline.playFromStart();
        
        if (code == KeyCode.LEFT || code == KeyCode.RIGHT ||
            code == KeyCode.UP || code == KeyCode.DOWN) {
            
            if (shift) startSelection();
            else clearSelection();
            
            if (code == KeyCode.LEFT) {
                m_cursor = m_navigator.moveBackward(m_cursor);
            } else if (code == KeyCode.RIGHT) {
                m_cursor = m_navigator.moveForward(m_cursor);
            } else if (code == KeyCode.UP) {
                m_cursor = moveUp(m_cursor);
            } else if (code == KeyCode.DOWN) {
                m_cursor = moveDown(m_cursor);
            }
            
            if (shift) updateSelection();
            
            ensureCursorVisible();
            
            event.consume();
            
        } else if (code == KeyCode.TAB) {
            clearSelection();
            
            if (shift) {
                m_cursor = m_navigator.moveToPreviousFocusable(m_cursor);
            } else {
                m_cursor = m_navigator.moveToNextFocusable(m_cursor);
            }
            
            ensureCursorVisible();
           
            event.consume();
            
        } else if (code == KeyCode.HOME) {
            if (shift) startSelection();
            else clearSelection();
            
            if (ctrl) {
                // Jump to document start
                m_cursor = new CursorSelectionSystem.CursorPosition();
            } else {
                // Jump to line start
                m_cursor = moveToLineStart(m_cursor);
            }
            
            if (shift) updateSelection();
            
            ensureCursorVisible();
           
            event.consume();
            
        } else if (code == KeyCode.END) {
            if (shift) startSelection();
            else clearSelection();
            
            if (ctrl) {
                // Jump to document end
                int totalLength = m_navigator.getTotalContentLength(m_segments);
                m_cursor = m_navigator.globalOffsetToPosition(totalLength);
            } else {
                // Jump to line end
                m_cursor = moveToLineEnd(m_cursor);
            }
            
            if (shift) updateSelection();
            
            ensureCursorVisible();
           
            event.consume();
            
        } else if (code == KeyCode.BACK_SPACE) {
            if (m_selection != null) {
                deleteSelection();
            } else {
                deleteBeforeCursor();
            }
            event.consume();
            
        } else if (code == KeyCode.DELETE) {
            if (m_selection != null) {
                deleteSelection();
            } else {
                deleteAfterCursor();
            }
            event.consume();
            
        } else if (code == KeyCode.A && ctrl) {
            selectAll(false);
            event.consume();
        } else if (code == KeyCode.C && ctrl) {
            // Copy to clipboard (would need clipboard integration)
            event.consume();
        } else if (code == KeyCode.X && ctrl) {
            // Cut to clipboard
            event.consume();
        } else if (code == KeyCode.V && ctrl) {
            // Paste from clipboard
            event.consume();
        }
        requestRender();
    }
    
    
    private CursorSelectionSystem.CursorPosition moveUp(CursorSelectionSystem.CursorPosition current) {
        if (m_layoutResult == null) return current;
        
        LayoutEngine.LayoutResult currentResult = m_layoutResult.findAtOffset(current.getGlobalOffset());
        if (currentResult == null) return current;
        
        Rectangle currentBounds = currentResult.bounds;
        int targetY = currentBounds.y - currentBounds.height / 2;
        
        // Find segment at this Y position
        List<LayoutEngine.LayoutResult> allResults = m_layoutResult.flatten();
        for (LayoutEngine.LayoutResult result : allResults) {
            if (result.bounds.y <= targetY && (result.bounds.y + result.bounds.height) > targetY) {
                return m_navigator.globalOffsetToPosition(result.globalStartOffset);
            }
        }
        
        return current;
    }
    
    
    private CursorSelectionSystem.CursorPosition moveDown(CursorSelectionSystem.CursorPosition current) {
        if (m_layoutResult == null) return current;
        
        LayoutEngine.LayoutResult currentResult = m_layoutResult.findAtOffset(current.getGlobalOffset());
        if (currentResult == null) return current;
        
        Rectangle currentBounds = currentResult.bounds;
        int targetY = currentBounds.y + currentBounds.height + currentBounds.height / 2;
        
        // Find segment at this Y position
        List<LayoutEngine.LayoutResult> allResults = m_layoutResult.flatten();
        for (LayoutEngine.LayoutResult result : allResults) {
            if (result.bounds.y <= targetY && (result.bounds.y + result.bounds.height) > targetY) {
                return m_navigator.globalOffsetToPosition(result.globalStartOffset);
            }
        }
        
        return current;
    }
    
   
    private CursorSelectionSystem.CursorPosition moveToLineStart(CursorSelectionSystem.CursorPosition current) {
        LayoutSegment segment = m_navigator.getSegmentAt(current);
        if (segment == null) return current;
        
        // For simplicity, move to start of current segment
        CursorSelectionSystem.CursorPosition start = current.copy();
        start.setLocalOffset(0);
        start.setGlobalOffset(current.getGlobalOffset() - current.getLocalOffset());
        return start;
    }
    
    
    private CursorSelectionSystem.CursorPosition moveToLineEnd(CursorSelectionSystem.CursorPosition current) {
        LayoutSegment segment = m_navigator.getSegmentAt(current);
        if (segment == null) return current;
        
        int contentLength = segment.getContentLength();
        CursorSelectionSystem.CursorPosition end = current.copy();
        end.setLocalOffset(contentLength);
        end.setGlobalOffset(current.getGlobalOffset() + (contentLength - current.getLocalOffset()));
        return end;
    }
    
    private void handleKeyTyped(KeyEvent event) {
        String character = event.getCharacter();
        
        // Filter control characters
        if (character == null || character.isEmpty() ||
            character.charAt(0) < 32 || character.equals("\u007F")) {
            event.consume();
            return;
        }
        
        // Check if we can edit at cursor
        if (!m_navigator.canEditAt(m_cursor)) {
            event.consume();
            return;
        }
        
        // Delete selection first if exists
        if (m_selection != null) {
            deleteSelection();
        }
        
        insertAtCursor(character);
        
        event.consume();
    }

     
    
  
    private void handleMousePressed(MouseEvent event) {
        requestFocus();
        
        if (m_layoutResult == null) return;
        
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        
        int x = (int) event.getX() - paddingLeft + m_scrollX;
        int y = (int) event.getY() - paddingTop + m_scrollY;
        
        LayoutEngine.LayoutResult clicked = m_layoutResult.findAtPoint(x, y);
        if (clicked != null) {
            // For text segments, find character position
            if (clicked.segment.getType() == LayoutSegment.SegmentType.TEXT) {
                int charPos = findCharacterAtPosition(clicked, x);
                m_cursor = m_navigator.globalOffsetToPosition(clicked.globalStartOffset + charPos);
            } else {
                m_cursor = m_navigator.globalOffsetToPosition(clicked.globalStartOffset);
            }
            
            m_selection = new CursorSelectionSystem.Selection(m_cursor, m_cursor);
            m_isSelecting = true;
            m_cursorVisible = true;
            requestRender();
        }
    }
    
  
    private int findCharacterAtPosition(LayoutEngine.LayoutResult result, int x) {
        NoteIntegerArray text = result.segment.getTextContent();
        if (text == null || text.length() == 0) return 0;
        
        String str = text.toString();
        Font font = result.segment.getStyle().getFont();
        
        int segmentX = result.bounds.x + result.segment.getLayout().padding.left;
        int relativeX = x - segmentX;
        
        // Binary search for closest character
        int left = 0;
        int right = str.length();
        int closest = 0;
        int closestDist = Integer.MAX_VALUE;
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String substr = str.substring(0, mid);
            int width = m_textRenderer.getTextWidth(substr, font);
            int dist = Math.abs(width - relativeX);
            
            if (dist < closestDist) {
                closestDist = dist;
                closest = mid;
            }
            
            if (width < relativeX) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return closest;
    }


    private void handleMouseDragged(MouseEvent event) {
        if (!m_isSelecting || m_layoutResult == null) return;
        double mouseX = event.getX();
        double mouseY = event.getY();
        
        pendingDragEvent.set(() -> processDragEvent(mouseX, mouseY));

        if (isDragProcessing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    Runnable task = pendingDragEvent.getAndSet(null);
                    if (task != null) {
                        task.run();
                    }
                } finally {
                    isDragProcessing.set(false);
                    
                    // If another event arrived, schedule it
                    if (pendingDragEvent.get() != null) {
                        handleMouseDragged(null); // Recursive call to re-schedule
                    }
                }
            }, TaskUtils.getVirtualExecutor());
        }
    }

    private void processDragEvent(double mouseX, double mouseY){
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        int paddingRight = m_insets.right;
        int paddingBottom = m_insets.bottom;
        
        int viewportWidth = m_preferredWidth - paddingLeft - paddingRight;
        int viewportHeight = m_preferredHeight - paddingTop - paddingBottom;
        

        
        int scrollDelta = 10;
        
        if (mouseX < paddingLeft && m_scrollX > 0) {
            m_scrollX = Math.max(0, m_scrollX - scrollDelta);
        } else if (mouseX > paddingLeft + viewportWidth && m_scrollX < m_maxScrollX) {
            m_scrollX = Math.min(m_maxScrollX, m_scrollX + scrollDelta);
        }
        
        if (mouseY < paddingTop && m_scrollY > 0) {
            m_scrollY = Math.max(0, m_scrollY - scrollDelta);
        } else if (mouseY > paddingTop + viewportHeight && m_scrollY < m_maxScrollY) {
            m_scrollY = Math.min(m_maxScrollY, m_scrollY + scrollDelta);
        }
        
        int x = (int) mouseX - paddingLeft + m_scrollX;
        int y = (int) mouseY - paddingTop + m_scrollY;
        
        x = Math.max(0, Math.min(x, m_layoutResult.bounds.width));
        y = Math.max(0, Math.min(y, m_layoutResult.bounds.height));
        
        LayoutEngine.LayoutResult dragged = m_layoutResult.findAtPoint(x, y);
        if (dragged != null) {
            if (dragged.segment.getType() == LayoutSegment.SegmentType.TEXT) {
                int charPos = findCharacterAtPosition(dragged, x);
                m_cursor = m_navigator.globalOffsetToPosition(dragged.globalStartOffset + charPos);
            } else {
                int segmentMidX = dragged.bounds.x + dragged.bounds.width / 2;
                if (x < segmentMidX) {
                    m_cursor = m_navigator.globalOffsetToPosition(dragged.globalStartOffset);
                } else {
                    m_cursor = m_navigator.globalOffsetToPosition(dragged.globalEndOffset);
                }
            }
            
            if (m_selection != null) {
                m_selection = new CursorSelectionSystem.Selection(
                    m_selection.getStart(),
                    m_cursor
                );
            }
            requestRender();
        }
        
    }

    private void handleMouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            selectWordAtCursor();
        } else if (event.getClickCount() == 3) {
            selectSegmentAtCursor();
        } else if (event.getClickCount() == 1) {
            // Single click - check for link
            checkLinkClick(event);
        }
    }

    private void checkLinkClick(MouseEvent event) {
        if (m_layoutResult == null) return;
        
        int paddingLeft = m_insets.left;
        int paddingTop = m_insets.top;
        
        // Convert screen coordinates to content coordinates
        // Account for padding and current scroll position
        int x = (int) event.getX() - paddingLeft + m_scrollX;
        int y = (int) event.getY() - paddingTop + m_scrollY;
        
        // Find the segment at the clicked point using the layout tree
        LayoutEngine.LayoutResult clicked = m_layoutResult.findAtPoint(x, y);
        if (clicked == null) return;
        
        // Check if this segment has link properties
        LinkProperties link = clicked.segment.getLinkProperties();
        if (link == null || link.url == null || link.url.isEmpty()) return;
        
        // We have a valid link - open it if HostServices available
        if (m_hostServices != null) {
            try {
                m_hostServices.showDocument(link.url);
                
                // Mark link as visited for potential visual feedback
                link.visited = true;
                
                // Mark segment dirty to trigger re-layout if needed
                clicked.segment.markDirty();
                
                // Force content layer redraw to show visited state
                m_contentDirty = true;
                requestRender();
                
            } catch (Exception e) {
                System.err.println("Failed to open link: " + link.url + " - " + e.getMessage());
            }
        } else {
            // No HostServices available - log for debugging
            //System.out.println("Link clicked but no HostServices available: " + link.url);
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        m_isSelecting = false;
        
        if (m_selection != null && m_selection.isEmpty()) {
            clearSelection();
        }
    }
    
    private void selectWordAtCursor() {
        LayoutSegment segment = m_navigator.getSegmentAt(m_cursor);
        if (segment == null || segment.getType() != LayoutSegment.SegmentType.TEXT) {
            return;
        }
        
        NoteIntegerArray text = segment.getTextContent();
        if (text == null || text.length() == 0) return;
        
        String str = text.toString();
        int localOffset = m_cursor.getLocalOffset();
        
        int start = localOffset;
        int end = localOffset;
        
        while (start > 0 && !Character.isWhitespace(str.charAt(start - 1))) {
            start--;
        }
        
        while (end < str.length() && !Character.isWhitespace(str.charAt(end))) {
            end++;
        }
        
        if (start < end) {
            int globalStart = m_cursor.getGlobalOffset() - localOffset + start;
            int globalEnd = m_cursor.getGlobalOffset() - localOffset + end;
            
            CursorSelectionSystem.CursorPosition startPos = m_navigator.globalOffsetToPosition(globalStart);
            CursorSelectionSystem.CursorPosition endPos = m_navigator.globalOffsetToPosition(globalEnd);
            
            m_selection = new CursorSelectionSystem.Selection(startPos, endPos);
            m_cursor = endPos;
           
            requestRender();
            
        }
    }
    
    private void selectSegmentAtCursor() {
        if (m_layoutResult == null) return;
        
        LayoutEngine.LayoutResult result = m_layoutResult.findAtOffset(m_cursor.getGlobalOffset());
        if (result == null) return;
        
        CursorSelectionSystem.CursorPosition startPos = 
            m_navigator.globalOffsetToPosition(result.globalStartOffset);
        CursorSelectionSystem.CursorPosition endPos = 
            m_navigator.globalOffsetToPosition(result.globalEndOffset);
        
        m_selection = new CursorSelectionSystem.Selection(startPos, endPos);
        m_cursor = endPos;
       
        requestRender();
        
    }

    private static final int RENDER_BUFFER = 500; // Render 500px beyond viewport
    private int m_lastRenderScrollX = 0;
    private int m_lastRenderScrollY = 0;
    
    private void handleScroll(ScrollEvent event) {
        double deltaX = event.getDeltaX();
        double deltaY = event.getDeltaY();
        boolean shift = event.isShiftDown();

        if (shift || Math.abs(deltaX) > Math.abs(deltaY)) {
            int scrollAmount = (int) (-deltaX);
            m_scrollX = Math.max(0, Math.min(m_scrollX + scrollAmount, m_maxScrollX));
        } else {
            int scrollAmount = (int) (-deltaY / 2);
            m_scrollY = Math.max(0, Math.min(m_scrollY + scrollAmount, m_maxScrollY));
        }

        checkScrollContentDirty();
        requestRender();
        event.consume();
    }

    
    private void startSelection() {
        if (m_selection == null) {
            m_selection = new CursorSelectionSystem.Selection(m_cursor, m_cursor);
        }
    }
    
    private void updateSelection() {
        if (m_selection != null) {
            m_selection = new CursorSelectionSystem.Selection(
                m_selection.getStart(),
                m_cursor
            );
           
        }
    }
    
    private void clearSelection() {
        if (m_selection != null) {
            m_selection = null;
        }
    }
    
    private void selectAll(boolean render) {
        if (m_segments.size() == 0) return;
        
        CursorSelectionSystem.CursorPosition start = new CursorSelectionSystem.CursorPosition();
        int totalLength = m_navigator.getTotalContentLength(m_segments);
        CursorSelectionSystem.CursorPosition end = m_navigator.globalOffsetToPosition(totalLength);
        
        m_selection = new CursorSelectionSystem.Selection(start, end);
        m_cursor = end;
        if(render){
            requestRender();
        }
    }
    


    private void insertAtCursor(String text) {
        LayoutSegment segment = m_navigator.getSegmentAt(m_cursor);
        if (segment == null || segment.getType() != LayoutSegment.SegmentType.TEXT) {
            return;
        }
        
        NoteIntegerArray content = segment.getTextContent();
        if (content == null) return;
        
        content.insert(m_cursor.getLocalOffset(), text);
        
        // NEW: Update navigator incrementally for text changes
        m_navigator.notifyTextInsert(m_cursor, text.length());
        
        m_cursor = m_navigator.moveForward(m_cursor);
        invalidateLayout();
        ensureCursorVisible();
        
    }

    private void deleteBeforeCursor() {
        if (m_cursor.getGlobalOffset() == 0) return;
        
        LayoutSegment segment = m_navigator.getSegmentAt(m_cursor);
        if (segment == null || segment.getType() != LayoutSegment.SegmentType.TEXT) {
            return;
        }
        
        if (!segment.getInteraction().editable) return;
        
        NoteIntegerArray content = segment.getTextContent();
        if (content == null || m_cursor.getLocalOffset() == 0) return;
        
        content.deleteCodePointAt(m_cursor.getLocalOffset() - 1);
        
        // NEW: Update navigator incrementally
        m_navigator.notifyTextDelete(m_cursor, 1);
        
        m_cursor = m_navigator.moveBackward(m_cursor);
        
        invalidateLayout();

        ensureCursorVisible();
        
    }

    private void deleteAfterCursor() {
        LayoutSegment segment = m_navigator.getSegmentAt(m_cursor);
        if (segment == null || segment.getType() != LayoutSegment.SegmentType.TEXT) {
            return;
        }
        
        if (!segment.getInteraction().editable) return;
        
        NoteIntegerArray content = segment.getTextContent();
        if (content == null || m_cursor.getLocalOffset() >= content.length()) {
            return;
        }
        
        content.deleteCodePointAt(m_cursor.getLocalOffset());
        
        // NEW: Update navigator incrementally
        m_navigator.notifyTextDelete(m_cursor, 1);
        
        invalidateLayout();
    }
    
    private void deleteSelection() {
        if (m_selection == null || m_selection.isEmpty()) return;
        
        CursorSelectionSystem.Selection normalized = m_selection.normalized();
        CursorSelectionSystem.CursorPosition start = normalized.getStart();
        CursorSelectionSystem.CursorPosition end = normalized.getEnd();
        
        if (start.getSegmentPath().equals(end.getSegmentPath())) {
            LayoutSegment segment = m_navigator.getSegmentAt(start);
            if (segment != null && segment.getType() == LayoutSegment.SegmentType.TEXT) {
                NoteIntegerArray text = segment.getTextContent();
                if (text != null) {
                    int deleteLen = end.getLocalOffset() - start.getLocalOffset();
                    text.delete(start.getLocalOffset(), end.getLocalOffset());
                    m_navigator.notifyTextDelete(start, deleteLen);
                }
            }
        } else {
            deleteMultiSegmentRange(start, end);
            m_navigator.invalidateCache();
        }
        
        m_cursor = normalized.getStart();
        m_selection = null;
        invalidateLayout();
        ensureCursorVisible();
        
    }
    
    private void deleteMultiSegmentRange(CursorSelectionSystem.CursorPosition start, CursorSelectionSystem.CursorPosition end) {
        int startGlobal = start.getGlobalOffset();
        int endGlobal = end.getGlobalOffset();
        
        List<LayoutEngine.LayoutResult> allResults = m_layoutResult != null ? 
            m_layoutResult.flatten() : new ArrayList<>();
        
        List<LayoutEngine.LayoutResult> affectedSegments = new ArrayList<>();
        for (LayoutEngine.LayoutResult result : allResults) {
            if (result.globalEndOffset > startGlobal && result.globalStartOffset < endGlobal) {
                affectedSegments.add(result);
            }
        }
        
        if (affectedSegments.isEmpty()) return;
        
        for (LayoutEngine.LayoutResult result : affectedSegments) {
            LayoutSegment segment = result.segment;
            
            if (segment.getType() != LayoutSegment.SegmentType.TEXT) continue;
            if (!segment.getInteraction().editable) continue;
            
            NoteIntegerArray text = segment.getTextContent();
            if (text == null) continue;
            
            int segStart = result.globalStartOffset;
          //  int segEnd = result.globalEndOffset;
            
            int deleteStart = Math.max(0, startGlobal - segStart);
            int deleteEnd = Math.min(text.length(), endGlobal - segStart);
            
            if (deleteEnd > deleteStart) {
                text.delete(deleteStart, deleteEnd);
            }
        }
    }
    
    /*private void updateSegmentInStorage(CursorSelectionSystem.CursorPosition position, LayoutSegment segment) {
        List<Integer> path = position.getSegmentPath();
        
        if (path.isEmpty()) return;
        
        NoteBytesArray current = m_segments;
        
        // Navigate to parent container
        for (int i = 0; i < path.size() - 1; i++) {
            int index = path.get(i);
            if (index >= current.size()) return;
            
            NoteBytesObject obj = (NoteBytesObject) current.get(index);
            LayoutSegment parent = new LayoutSegment(obj);
            
            if (!parent.isContainer()) return;
            
            current = parent.getChildren();
        }
        
        // Update the segment directly in the array
        int finalIndex = path.get(path.size() - 1);
        if (finalIndex < current.size()) {
            // Get the existing NoteBytesObject
            NoteBytesObject existing = (NoteBytesObject) current.get(finalIndex);
            
            // Update its content field directly (for TEXT segments)
            if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
                existing.add("content", segment.getTextContent());
            }
            // No need to replace the entire object in the array
            // The reference is already there and we've mutated it
        }
    }*/
    
    private void rebuildNavigator() {
        m_navigator = new CursorSelectionSystem.CursorNavigator(m_segments);
    }
    
    public NoteBytesArray getSegments() {
        return m_segments;
    }
    
    public void setSegments(NoteBytesArray segments, boolean render) {
        m_segments = segments;
        rebuildNavigator();
        m_cursor = new CursorSelectionSystem.CursorPosition();
        m_selection = null;
        invalidateLayout();
    }
    
    public void addSegment(LayoutSegment segment) {
        m_segments.add(segment.getData());
        m_navigator.invalidateCache();
        invalidateLayout();
    }
    
    public void addSegment(int index, LayoutSegment segment) {
        m_segments.add(index, segment.getData());
        m_navigator.invalidateCache();
        invalidateLayout();
    }
    
    public void removeSegment(int index) {
        if (index >= 0 && index < m_segments.size()) {
            m_segments.remove(index);
            m_navigator.invalidateCache();
            invalidateLayout();
        }
    }
    
    public void clear() {
        m_segments.clear();
        rebuildNavigator();
        m_cursor = new CursorSelectionSystem.CursorPosition();
        m_selection = null;
        m_scrollX = 0;
        m_scrollY = 0;
        invalidateLayout();
    }
    
    public CursorSelectionSystem.CursorPosition getCursorPosition() {
        return m_cursor;
    }
    
    public void setCursorPosition(CursorSelectionSystem.CursorPosition position, boolean render) {
        m_cursor = position;
        ensureCursorVisible();
        if(render){
            requestRender();
        }
    }
    
    public CursorSelectionSystem.Selection getSelection() {
        return m_selection;
    }
    
    public String getSelectedText() {
        if (m_selection == null || m_selection.isEmpty()) {
            return "";
        }
        return m_navigator.getTextInRange(m_selection);
    }
    
    public LayoutSegment getSegmentAtCursor() {
        return m_navigator.getSegmentAt(m_cursor);
    }
    
    public LayoutEngine.LayoutResult getLayoutResultAtCursor() {
        if (m_layoutResult == null) return null;
        return m_layoutResult.findAtOffset(m_cursor.getGlobalOffset());
    }
    
    public List<LayoutEngine.LayoutResult> getAllLayoutResults() {
        if (m_layoutResult == null) return new ArrayList<>();
        return m_layoutResult.flatten();
    }

    // ======== Getters / setters ==========

    public void setHostServices(HostServices hostServices) {
        m_hostServices = hostServices;
    }
    
    public void setBackgroundColor(Color color, boolean render) {
        m_backgroundColor = color;
        if(render){
            requestRender();
        }
    }
    
    public Color getBackgroundColor() {
        return m_backgroundColor;
    }
    
    public void setCursorColor(Color color, boolean render) {
        m_cursorColor = color;
        if(render){
            requestRender();
        }
    }
    
    public Color getCursorColor() {
        return m_cursorColor;
    }
    
    public void setSelectionColor(Color color, boolean render) {
        m_selectionColor = color;
        if(render){
            requestRender();
        }
    }
    
    public Color getSelectionColor() {
        return m_selectionColor;
    }
    
    public void setInsets(Insets insets) {
        m_insets = insets;
        invalidateLayout();
    }
    
    public Insets getInsets() {
        return m_insets;
    }
    
    public void setPreferredSize(int width, int height) {
        m_preferredWidth = width;
        m_preferredHeight = height;
        m_viewportWidth = width + VIEWPORT_BUFFER;
        m_viewportHeight = height + VIEWPORT_BUFFER;
        invalidateLayout();
    }
    
    public int getPreferredWidth() {
        return m_preferredWidth;
    }
    
    public int getPreferredHeight() {
        return m_preferredHeight;
    }
    
    public int getScrollX() {
        return m_scrollX;
    }
    
    public int getScrollY() {
        return m_scrollY;
    }

    public int getMaxScrollX() {
        return m_maxScrollX;
    }

    public int getMaxScrollY() {
        return m_maxScrollY;
    }

    public void setScrollX(int x, boolean render) {
        m_scrollX = Math.max(0, Math.min(x, m_maxScrollX));
        checkScrollContentDirty();
        if(render){
            requestRender();
        }
    }
    
    public void setScrollY(int y, boolean render) {
        m_scrollY = Math.max(0, Math.min(y, m_maxScrollY));
        checkScrollContentDirty();
        if(render){
            requestRender();
        }
    }

     private void checkScrollContentDirty() {
        if (Math.abs(m_scrollX - m_lastRenderScrollX) > RENDER_BUFFER ||
            Math.abs(m_scrollY - m_lastRenderScrollY) > RENDER_BUFFER) {
            m_contentDirty = true;
        }
    }
    
    public void scrollToSegment(LayoutSegment segment, boolean render) {
        if (m_layoutResult == null) return;
        
        List<LayoutEngine.LayoutResult> results = m_layoutResult.flatten();
        for (LayoutEngine.LayoutResult result : results) {
            if (result.segment == segment) {
                Rectangle bounds = result.bounds;
                
                int paddingLeft = m_insets.left;
                int paddingTop = m_insets.top;
                int paddingRight = m_insets.right;
                int paddingBottom = m_insets.bottom;
                
                int viewportWidth = m_preferredWidth - paddingLeft - paddingRight;
                int viewportHeight = m_preferredHeight - paddingTop - paddingBottom;
                
                m_scrollX = Math.max(0, bounds.x - viewportWidth / 2);
                m_scrollY = Math.max(0, bounds.y - viewportHeight / 2);
                
                m_scrollX = Math.max(0, Math.min(m_scrollX, m_maxScrollX));
                m_scrollY = Math.max(0, Math.min(m_scrollY, m_maxScrollY));
                if(render){
                    requestRender();
                }
                break;
            }
        }
    }
    
    public static LayoutSegment createTextSegment(String text) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getTextContent().set(text);
        return segment;
    }
    
    public static LayoutSegment createTextSegment(String text, Font font, Color color) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getTextContent().set(text);
        segment.getStyle().fontName = font.getName();
        segment.getStyle().fontSize = font.getSize();
        segment.getStyle().textColor = color;
        return segment;
    }
    
    public static LayoutSegment createContainer() {
        return new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
    }
    
    public static LayoutSegment createSpacer(double width, double height) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.SPACER);
        segment.getLayout().width = LayoutSegment.Dimension.px(width);
        segment.getLayout().height = LayoutSegment.Dimension.px(height);
        return segment;
    }
    
    public static NoteBytesArray buildSimpleDocument(String... paragraphs) {
        NoteBytesArray segments = new NoteBytesArray();
        
        for (String para : paragraphs) {
            LayoutSegment segment = createTextSegment(para);
            segment.getLayout().display = LayoutSegment.Display.BLOCK;
            segment.getLayout().margin = new Insets(0, 0, 10, 0);
            segment.getInteraction().editable = true;
            segments.add(segment.getData());
        }
        
        return segments;
    }
    
    public static NoteBytesArray buildFormLayout() {
        NoteBytesArray segments = new NoteBytesArray();
        
        LayoutSegment title = createTextSegment("Contact Form");
        title.getLayout().display = LayoutSegment.Display.BLOCK;
        title.getStyle().fontSize = 24;
        title.getStyle().bold = true;
        title.getLayout().margin = new Insets(0, 0, 20, 0);
        title.getInteraction().editable = false;
        title.getInteraction().focusable = false;
        segments.add(title.getData());
        
        LayoutSegment nameLabel = createTextSegment("Name:");
        nameLabel.getLayout().display = LayoutSegment.Display.BLOCK;
        nameLabel.getLayout().margin = new Insets(0, 0, 5, 0);
        nameLabel.getInteraction().editable = false;
        nameLabel.getInteraction().focusable = false;
        segments.add(nameLabel.getData());
        
        LayoutSegment nameInput = createTextSegment("");
        nameInput.getLayout().display = LayoutSegment.Display.BLOCK;
        nameInput.getLayout().width = LayoutSegment.Dimension.percent(100);
        nameInput.getLayout().padding = new Insets(8, 0, 0, 0);
        nameInput.getLayout().margin = new Insets(0, 0, 15, 0);
        nameInput.getLayout().backgroundColor = new Color(245, 245, 245);
        nameInput.getLayout().borderColor = Color.GRAY;
        nameInput.getLayout().borderWidth = 1;
        nameInput.getInteraction().editable = true;
        nameInput.getInteraction().focusable = true;
        segments.add(nameInput.getData());
        
        LayoutSegment emailLabel = createTextSegment("Email:");
        emailLabel.getLayout().display = LayoutSegment.Display.BLOCK;
        emailLabel.getLayout().margin = new Insets(0, 0, 5, 0);
        emailLabel.getInteraction().editable = false;
        emailLabel.getInteraction().focusable = false;
        segments.add(emailLabel.getData());
        
        LayoutSegment emailInput = createTextSegment("");
        emailInput.getLayout().display = LayoutSegment.Display.BLOCK;
        emailInput.getLayout().width = LayoutSegment.Dimension.percent(100);
        emailInput.getLayout().padding = new Insets(8, 0, 0, 0);
        emailInput.getLayout().margin = new Insets(0, 0, 15, 0);
        emailInput.getLayout().backgroundColor = new Color(245, 245, 245);
        emailInput.getLayout().borderColor = Color.GRAY;
        emailInput.getLayout().borderWidth = 1;
        emailInput.getInteraction().editable = true;
        emailInput.getInteraction().focusable = true;
        segments.add(emailInput.getData());
        
        return segments;
    }


    
    public CompletableFuture<Void> shutdown() {
        
       
            
        if (m_contentLayer != null) {
            m_contentLayer.flush();
            m_contentLayer = null;
        }
        
    
        if (m_cursorTimeline != null) {
            m_cursorTimeline.stop();
        }
        m_layoutCache.clear();

        if (widthListener != null) {
            widthProperty().removeListener(widthListener);
        }
        if (heightListener != null) {
            heightProperty().removeListener(heightListener);
        }

        
        
        m_segments = null;
        m_layoutEngine = null;
        m_navigator = null;
        m_layoutResult = null;
       
        return super.shutdown();
            
    }
}