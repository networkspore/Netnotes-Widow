package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.gui.fx.display.TextRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout Engine for computing segment positions and sizes.
 * 
 * Layout Process:
 * 1. Measure - Calculate intrinsic sizes (min/max/preferred)
 * 2. Layout - Assign final positions and sizes based on constraints
 * 3. Build - Create LayoutResult tree for rendering
 * 
 * Supports:
 * - Block layout (vertical stacking)
 * - Inline layout (horizontal flowing with wrapping)
 * - Inline-block layout (inline but with width/height)
 * - Percentage dimensions (relative to parent)
 * - Margins and padding
 * - Display modes: block, inline, inline-block, hidden, none
 */
public class LayoutEngine {
    
    private final TextRenderer textRenderer = TextRenderer.getInstance();
    
    /**
     * Constraints for layout computation
     */
    public static class Constraints {
        public int maxWidth;
        public int maxHeight;
        public boolean widthConstrained;
        public boolean heightConstrained;
        
        public Constraints(int maxWidth, int maxHeight) {
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.widthConstrained = maxWidth != Integer.MAX_VALUE;
            this.heightConstrained = maxHeight != Integer.MAX_VALUE;
        }
        
        public static Constraints loose(int width, int height) {
            return new Constraints(width, height);
        }
        
        public static Constraints tight(int width, int height) {
            Constraints c = new Constraints(width, height);
            c.widthConstrained = true;
            c.heightConstrained = true;
            return c;
        }
        
        public Constraints constrainWidth(int width) {
            return new Constraints(
                Math.min(maxWidth, width),
                maxHeight
            );
        }
        
        public Constraints deflate(Insets insets) {
            return new Constraints(
                Math.max(0, maxWidth - insets.left - insets.right),
                Math.max(0, maxHeight - insets.top - insets.bottom)
            );
        }
    }
    
    /**
     * Measured size of a segment
     */
    public static class MeasuredSize {
        public int width;
        public int height;
        public int minWidth;
        public int maxWidth;
        
        public MeasuredSize(int width, int height) {
            this.width = width;
            this.height = height;
            this.minWidth = width;
            this.maxWidth = width;
        }
        
        public MeasuredSize(int width, int height, int minWidth, int maxWidth) {
            this.width = width;
            this.height = height;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
        }
    }
    
    /**
     * Result of laying out a segment (includes all children)
     */
    public static class LayoutResult {
        public LayoutSegment segment;
        public Rectangle bounds;
        public List<LayoutResult> children;
        public int globalStartOffset;
        public int globalEndOffset;
        
        public LayoutResult(LayoutSegment segment) {
            this.segment = segment;
            this.bounds = new Rectangle(0, 0, 0, 0);
            this.children = new ArrayList<>();
        }
        
        /**
         * Find layout result at global offset
         */
        public LayoutResult findAtOffset(int offset) {
            if (offset < globalStartOffset || offset > globalEndOffset) {
                return null;
            }
            
            // Check children first (depth-first search)
            for (LayoutResult child : children) {
                LayoutResult found = child.findAtOffset(offset);
                if (found != null) return found;
            }
            
            // This node contains the offset
            return this;
        }
        
        /**
         * Find layout result at point
         */
        public LayoutResult findAtPoint(int x, int y) {
            if (!bounds.contains(x, y)) {
                return null;
            }
            
            // Check children first (prefer leaf nodes)
            for (LayoutResult child : children) {
                LayoutResult found = child.findAtPoint(x, y);
                if (found != null) return found;
            }
            
            // This node contains the point
            return this;
        }
        
        /**
         * Get all layout results in depth-first order
         */
        public List<LayoutResult> flatten() {
            List<LayoutResult> results = new ArrayList<>();
            results.add(this);
            for (LayoutResult child : children) {
                results.addAll(child.flatten());
            }
            return results;
        }
    }
    
    /**
     * Context for layout computation
     */
    private static class LayoutContext {
        int currentX = 0;
        int currentY = 0;
        int lineHeight = 0;
        int maxLineWidth = 0;
        int globalOffset = 0;
        List<LayoutResult> currentLine = new ArrayList<>();
    }
    
    /**
     * Perform full layout on segment tree
     */
    public LayoutResult layout(NoteBytesArray segments, Constraints constraints) {
        // Create virtual root container
        LayoutSegment root = new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
        root.getChildren().clear();
        
        // Copy segments into root
        for (int i = 0; i < segments.size(); i++) {
            root.getChildren().add(segments.get(i));
        }
        
        LayoutResult result = new LayoutResult(root);
        LayoutContext ctx = new LayoutContext();
        
        layoutContainer(root, constraints, result, ctx);
        
        return result;
    }
    
    /**
     * Layout a container segment
     */
    private void layoutContainer(
        LayoutSegment segment,
        Constraints constraints,
        LayoutResult result,
        LayoutContext ctx
    ) {
        if (!segment.isContainer() || !segment.hasChildren()) {
            return;
        }
        
        // Apply padding
        Insets padding = segment.getLayout().padding;
        Constraints innerConstraints = constraints.deflate(padding);
        
        int startX = padding.left;
        int startY = padding.top;
        
        ctx.currentX = startX;
        ctx.currentY = startY;
        ctx.lineHeight = 0;
        ctx.maxLineWidth = 0;
        
        NoteBytesArray children = segment.getChildren();
        
        for (int i = 0; i < children.size(); i++) {
            NoteBytes item = children.get(i);
            if (!(item instanceof NoteBytesObject)) continue;
            
            LayoutSegment childSegment = new LayoutSegment((NoteBytesObject) item);
            
            // Skip display:none
            if (childSegment.getLayout().display == LayoutSegment.Display.NONE) {
                continue;
            }
            
            LayoutResult childResult = new LayoutResult(childSegment);
            result.children.add(childResult);
            
            // Measure child
            MeasuredSize measured = measure(childSegment, innerConstraints);
            
            // Layout based on display type
            switch (childSegment.getLayout().display) {
                case BLOCK:
                    layoutBlock(childSegment, measured, innerConstraints, childResult, ctx);
                    break;
                    
                case INLINE:
                case INLINE_BLOCK:
                    layoutInline(childSegment, measured, innerConstraints, childResult, ctx, startX);
                    break;
                    
                case HIDDEN:
                    // Takes space but not visible
                    layoutBlock(childSegment, measured, innerConstraints, childResult, ctx);
                    break;
                    
                default:
                    break;
            }
            
            // Recursively layout children
            if (childSegment.isContainer()) {
                Constraints childConstraints = new Constraints(
                    childResult.bounds.width,
                    childResult.bounds.height
                );
                layoutContainer(childSegment, childConstraints, childResult, ctx);
            }
        }
        
        // Flush remaining inline elements
        if (!ctx.currentLine.isEmpty()) {
            flushLine(ctx, startX, innerConstraints.maxWidth);
        }
        
        // Set container bounds
        int contentWidth = ctx.maxLineWidth;
        int contentHeight = ctx.currentY;
        
        result.bounds.width = contentWidth + padding.left + padding.right;
        result.bounds.height = contentHeight + padding.top + padding.bottom;
    }
    
    /**
     * Layout a block-level segment
     */
    private void layoutBlock(
        LayoutSegment segment,
        MeasuredSize measured,
        Constraints constraints,
        LayoutResult result,
        LayoutContext ctx
    ) {
        // Flush any pending inline elements
        if (!ctx.currentLine.isEmpty()) {
            flushLine(ctx, 0, constraints.maxWidth);
        }
        
        // Apply margins
        Insets margin = segment.getLayout().margin;
        ctx.currentY += margin.top;
        
        // Resolve width
        int width = resolveWidth(segment, measured, constraints);
        
        // Resolve height
        int height = resolveHeight(segment, measured, constraints);
        
        // Position
        result.bounds.x = margin.left;
        result.bounds.y = ctx.currentY;
        result.bounds.width = width;
        result.bounds.height = height;
        
        // Update cursor offsets
        result.globalStartOffset = ctx.globalOffset;
        ctx.globalOffset += segment.getContentLength();
        result.globalEndOffset = ctx.globalOffset;
        
        // Advance Y
        ctx.currentY += height + margin.bottom;
        ctx.maxLineWidth = Math.max(ctx.maxLineWidth, width + margin.left + margin.right);
    }
    
    /**
     * Layout an inline or inline-block segment
     */
    private void layoutInline(
        LayoutSegment segment,
        MeasuredSize measured,
        Constraints constraints,
        LayoutResult result,
        LayoutContext ctx,
        int startX
    ) {
        Insets margin = segment.getLayout().margin;
        
        int width = resolveWidth(segment, measured, constraints);
        int height = resolveHeight(segment, measured, constraints);
        
        int totalWidth = width + margin.left + margin.right;
        
        // Check if we need to wrap to new line
        if (ctx.currentX + totalWidth > constraints.maxWidth && ctx.currentX > startX) {
            flushLine(ctx, startX, constraints.maxWidth);
        }
        
        // Position on current line
        result.bounds.x = ctx.currentX + margin.left;
        result.bounds.y = ctx.currentY + margin.top;
        result.bounds.width = width;
        result.bounds.height = height;
        
        // Update cursor offsets
        result.globalStartOffset = ctx.globalOffset;
        ctx.globalOffset += segment.getContentLength();
        result.globalEndOffset = ctx.globalOffset;
        
        // Add to current line
        ctx.currentLine.add(result);
        ctx.currentX += totalWidth;
        ctx.lineHeight = Math.max(ctx.lineHeight, height + margin.top + margin.bottom);
    }
    
    /**
     * Flush current line of inline elements
     */
    private void flushLine(LayoutContext ctx, int startX, int maxWidth) {
        if (ctx.currentLine.isEmpty()) return;
        
        // Update max width
        ctx.maxLineWidth = Math.max(ctx.maxLineWidth, ctx.currentX);
        
        // Move to next line
        ctx.currentY += ctx.lineHeight;
        ctx.currentX = startX;
        ctx.lineHeight = 0;
        ctx.currentLine.clear();
    }
    
    /**
     * Measure intrinsic size of a segment
     */
    private MeasuredSize measure(LayoutSegment segment, Constraints constraints) {
        switch (segment.getType()) {
            case TEXT:
                return measureText(segment, constraints);
                
            case CONTAINER:
                return measureContainer(segment, constraints);
                
            case IMAGE:
                return measureImage(segment, constraints);
                
            case SPACER:
                return new MeasuredSize(0, 0);
                
            default:
                return new MeasuredSize(0, 0);
        }
    }
    
    /**
     * Measure text segment
     */
    private MeasuredSize measureText(LayoutSegment segment, Constraints constraints) {
        NoteIntegerArray text = segment.getTextContent();
        if (text == null || text.length() == 0) {
            Font font = segment.getStyle().getFont();
            FontMetrics metrics = textRenderer.getMetrics(font);
            return new MeasuredSize(0, metrics.getHeight(), 0, 0);
        }
        
        String str = text.toString();
        Font font = segment.getStyle().getFont();
        
        // Measure text
        int textWidth = textRenderer.getTextWidth(str, font);
        FontMetrics metrics = textRenderer.getMetrics(font);
        int textHeight = metrics.getHeight();
        
        // Handle word wrapping for block/inline-block
        if (segment.getLayout().display == LayoutSegment.Display.BLOCK ||
            segment.getLayout().display == LayoutSegment.Display.INLINE_BLOCK) {
            
            if (textWidth > constraints.maxWidth) {
                // Need to wrap - estimate height based on line count
                int estimatedLines = (textWidth / constraints.maxWidth) + 1;
                textHeight = metrics.getHeight() * estimatedLines;
                textWidth = constraints.maxWidth;
            }
        }
        
        Insets padding = segment.getLayout().padding;
        int totalWidth = textWidth + padding.left + padding.right;
        int totalHeight = textHeight + padding.top + padding.bottom;
        
        return new MeasuredSize(totalWidth, totalHeight, 0, Integer.MAX_VALUE);
    }
    
    /**
     * Measure container segment
     */
    private MeasuredSize measureContainer(LayoutSegment segment, Constraints constraints) {
        if (!segment.hasChildren()) {
            Insets padding = segment.getLayout().padding;
            return new MeasuredSize(
                padding.left + padding.right,
                padding.top + padding.bottom
            );
        }
        
        // Recursively measure children
        Insets padding = segment.getLayout().padding;
        Constraints innerConstraints = constraints.deflate(padding);
        
        int maxWidth = 0;
        int totalHeight = 0;
        
        NoteBytesArray children = segment.getChildren();
        for (int i = 0; i < children.size(); i++) {
            NoteBytes item = children.get(i);
            if (!(item instanceof NoteBytesObject)) continue;
            
            LayoutSegment child = new LayoutSegment((NoteBytesObject) item);
            
            if (child.getLayout().display == LayoutSegment.Display.NONE) {
                continue;
            }
            
            MeasuredSize childSize = measure(child, innerConstraints);
            
            if (child.getLayout().display == LayoutSegment.Display.BLOCK) {
                maxWidth = Math.max(maxWidth, childSize.width);
                totalHeight += childSize.height;
            } else {
                // Inline - for now just stack (proper inline layout happens later)
                maxWidth = Math.max(maxWidth, childSize.width);
                totalHeight += childSize.height;
            }
        }
        
        return new MeasuredSize(
            maxWidth + padding.left + padding.right,
            totalHeight + padding.top + padding.bottom
        );
    }
    
    /**
     * Measure image segment
     */
    private MeasuredSize measureImage(LayoutSegment segment, Constraints constraints) {
        // TODO: Read image dimensions from binary data
        // For now, return default size or specified dimensions
        
        int width = 100;
        int height = 100;
        
        Insets padding = segment.getLayout().padding;
        return new MeasuredSize(
            width + padding.left + padding.right,
            height + padding.top + padding.bottom
        );
    }
    
    /**
     * Resolve final width from layout properties and measured size
     */
    private int resolveWidth(
        LayoutSegment segment,
        MeasuredSize measured,
        Constraints constraints
    ) {
        LayoutSegment.LayoutProperties layout = segment.getLayout();
        
        if (!layout.width.isAuto()) {
            int resolved = layout.width.resolve(constraints.maxWidth);
            
            // Subtract padding
            Insets padding = layout.padding;
            resolved -= (padding.left + padding.right);
            
            return Math.max(0, resolved);
        }
        
        // Auto width
        if (layout.display == LayoutSegment.Display.BLOCK) {
            // Block takes full width
            Insets padding = layout.padding;
            return Math.max(0, constraints.maxWidth - padding.left - padding.right);
        } else {
            // Inline/inline-block uses measured width
            Insets padding = layout.padding;
            return Math.max(0, measured.width - padding.left - padding.right);
        }
    }
    
    /**
     * Resolve final height from layout properties and measured size
     */
    private int resolveHeight(
        LayoutSegment segment,
        MeasuredSize measured,
        Constraints constraints
    ) {
        LayoutSegment.LayoutProperties layout = segment.getLayout();
        
        if (!layout.height.isAuto()) {
            int resolved = layout.height.resolve(constraints.maxHeight);
            
            // Subtract padding
            Insets padding = layout.padding;
            resolved -= (padding.top + padding.bottom);
            
            return Math.max(0, resolved);
        }
        
        // Auto height - use measured
        Insets padding = layout.padding;
        return Math.max(0, measured.height - padding.top - padding.bottom);
    }
}