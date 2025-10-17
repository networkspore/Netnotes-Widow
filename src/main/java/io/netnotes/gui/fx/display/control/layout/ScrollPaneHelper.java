package io.netnotes.gui.fx.display.control.layout;

import javafx.beans.binding.DoubleExpression;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Helper class for managing ScrollPane sizing through DeferredLayoutManager.
 * Ensures content regions properly size to fit the scrollpane viewport without flickering.
 */
public class ScrollPaneHelper {
    public final static int VIEWPORT_HEIGHT_OFFSET = 5;
    public final static int VIEWPORT_WIDTH_OFFSET = 1;
    public final static int CONTENT_HEIGHT_OFFSET = 3;
    public final static int CONTENT_WIDTH_OFFSET = 2;
    
    private final ScrollPane scrollPane;
    private final Region contentRegion;
    private final Stage stage;
    private final DoubleExpression baseWidth;
    private final DoubleExpression baseHeight;
    private final DoubleExpression[] widthOffsets;
    private final DoubleExpression[] heightOffsets;
    
    /**
     * Creates a ScrollPaneHelper that manages sizing through DeferredLayoutManager.
     * 
     * @param stage The Stage this ScrollPane belongs to
     * @param scrollPane The ScrollPane to manage
     * @param contentRegion The content Region inside the ScrollPane
     * @param baseWidth The base width property to calculate from
     * @param baseHeight The base height property to calculate from
     */
    public ScrollPaneHelper(Stage stage, ScrollPane scrollPane, Region contentRegion, 
                           DoubleExpression baseWidth, DoubleExpression baseHeight) {
        this(stage, scrollPane, contentRegion, baseWidth, baseHeight, null, null);
    }
    
    /**
     * Creates a ScrollPaneHelper with additional width and height offsets.
     * Useful when you have other UI elements that need to be accounted for.
     * 
     * @param stage The Stage this ScrollPane belongs to
     * @param scrollPane The ScrollPane to manage
     * @param contentRegion The content Region inside the ScrollPane
     * @param baseWidth The base width property
     * @param baseHeight The base height property
     * @param widthOffsets Additional properties to subtract from width
     * @param heightOffsets Additional properties to subtract from height
     */
    public ScrollPaneHelper(Stage stage, ScrollPane scrollPane, Region contentRegion,
                           DoubleExpression baseWidth, DoubleExpression baseHeight,
                           DoubleExpression[] widthOffsets, DoubleExpression[] heightOffsets) {
        this.stage = stage;
        this.scrollPane = scrollPane;
        this.contentRegion = contentRegion;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.widthOffsets = widthOffsets;
        this.heightOffsets = heightOffsets;
        
        registerWithLayoutManager();
    }
    
    private void registerWithLayoutManager() {
        // Register the ScrollPane viewport
        DeferredLayoutManager.register(stage, scrollPane, _ -> {
            double width = calculateWidth();
            double height = calculateHeight();
            
            return new LayoutData.Builder()
                .width(width)
                .height(height)
                .build();
        });
        
        // Register the content region to match viewport (minus scrollbar space)
        DeferredLayoutManager.register(stage, contentRegion, _ -> {
            double width = calculateWidth() - CONTENT_WIDTH_OFFSET;
            double height = calculateHeight() - CONTENT_HEIGHT_OFFSET;
            
            return new LayoutData.Builder()
                .width(Math.max(0, width))
                .height(Math.max(0, height))
                .build();
        });
        
        // Listen for base property changes
        if (baseWidth != null) {
            baseWidth.addListener((_, _, _) -> markDirty());
        }
        if (baseHeight != null) {
            baseHeight.addListener((_, _, _) -> markDirty());
        }
        
        // Listen for offset property changes
        if (widthOffsets != null) {
            for (DoubleExpression offset : widthOffsets) {
                if (offset != null) {
                    offset.addListener((_, _, _) -> markDirty());
                }
            }
        }
        if (heightOffsets != null) {
            for (DoubleExpression offset : heightOffsets) {
                if (offset != null) {
                    offset.addListener((_, _, _) -> markDirty());
                }
            }
        }
    }
    
    private double calculateWidth() {
        double width = baseWidth != null ? baseWidth.get() : 0;
        
        // Subtract width offsets
        if (widthOffsets != null) {
            for (DoubleExpression offset : widthOffsets) {
                if (offset != null) {
                    width -= offset.get();
                }
            }
        }
        
        // Final viewport offset
        width -= VIEWPORT_WIDTH_OFFSET;
        
        return Math.max(0, width);
    }
    
    private double calculateHeight() {
        double height = baseHeight != null ? baseHeight.get() : 0;
        
        // Subtract height offsets
        if (heightOffsets != null) {
            for (DoubleExpression offset : heightOffsets) {
                if (offset != null) {
                    height -= offset.get();
                }
            }
        }
        
        // Final viewport offset
        height -= VIEWPORT_HEIGHT_OFFSET;
        
        return Math.max(0, height);
    }
    
    private void markDirty() {
        DeferredLayoutManager.markDirty(scrollPane);
        DeferredLayoutManager.markDirty(contentRegion);
    }
    
    /**
     * Manually trigger a layout update
     */
    public void refresh() {
        markDirty();
    }
    
    public ScrollPane getScrollPane() {
        return scrollPane;
    }
    
    public Region getContentRegion() {
        return contentRegion;
    }
}