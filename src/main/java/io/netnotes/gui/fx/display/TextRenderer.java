package io.netnotes.gui.fx.display;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Singleton text renderer that uses pooled graphics contexts.
 * All text rendering should go through this to maximize resource reuse.
 */
public class TextRenderer {
    
    private static final TextRenderer INSTANCE = new TextRenderer();
    
    private final GraphicsContextPool contextPool;
    private final FontMetricsCache metricsCache;
    
    private TextRenderer() {
        this.contextPool = GraphicsContextPool.getInstance();
        this.metricsCache = FontMetricsCache.getInstance();
    }
    
    /**
     * Get the singleton instance
     */
    public static TextRenderer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Render text to a BufferedImage using pooled graphics context
     * 
     * @param width Width of the image
     * @param height Height of the image
     * @param renderer Callback that does the actual rendering
     * @return The rendered BufferedImage
     */
    public BufferedImage render(int width, int height, Consumer<Graphics2D> renderer) {
        try (GraphicsContextPool.GraphicsContext ctx = contextPool.acquire(width, height)) {
            Graphics2D g2d = ctx.getGraphics();
            
            // Clear first
            ctx.clear();
            
            // Let the callback do the rendering
            renderer.accept(g2d);
            
            // Return the image (note: this returns the pooled image, so copy if needed for long-term use)
            return ctx.getImage();
        }
    }
    
    /**
     * Render text to a BufferedImage and copy the result for safe keeping
     * Use this when you need to keep the image after the render call
     */
    public BufferedImage renderAndCopy(int width, int height, Consumer<Graphics2D> renderer) {
        BufferedImage pooledImage = render(width, height, renderer);
        
        // Create a copy
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(pooledImage, 0, 0, null);
        g2d.dispose();
        
        return copy;
    }
    
    /**
     * Render text with specific configuration
     */
    public BufferedImage renderText(TextRenderConfig config) {
        return render(config.width, config.height, g2d -> {
            // Apply font
            g2d.setFont(config.font);
            
            // Apply colors
            g2d.setColor(config.backgroundColor);
            g2d.fillRect(0, 0, config.width, config.height);
            
            // Draw text
            FontMetrics metrics = metricsCache.getMetrics(config.font);
            int textY = (config.height + metrics.getAscent() - metrics.getDescent()) / 2;
            
            g2d.setColor(config.textColor);
            g2d.drawString(config.text, config.x, textY);
        });
    }
    
    /**
     * Configuration for simple text rendering
     */
    public static class TextRenderConfig {
        public int width;
        public int height;
        public String text;
        public Font font;
        public Color textColor = Color.BLACK;
        public Color backgroundColor = new Color(0, 0, 0, 0); // Transparent
        public int x = 0;
        
        public TextRenderConfig(int width, int height, String text, Font font) {
            this.width = width;
            this.height = height;
            this.text = text;
            this.font = font;
        }
        
        public TextRenderConfig withTextColor(Color color) {
            this.textColor = color;
            return this;
        }
        
        public TextRenderConfig withBackgroundColor(Color color) {
            this.backgroundColor = color;
            return this;
        }
        
        public TextRenderConfig withX(int x) {
            this.x = x;
            return this;
        }
    }
    
    /**
     * Measure text dimensions without rendering
     */
    public Dimension measureText(String text, Font font) {
        FontMetrics metrics = metricsCache.getMetrics(font);
        int width = metricsCache.getStringWidth(font, text);
        int height = metrics.getHeight();
        return new Dimension(width, height);
    }
    
    /**
     * Get precise text width
     */
    public int getTextWidth(String text, Font font) {
        return metricsCache.getStringWidth(font, text);
    }
    
    /**
     * Get precise text width (high precision for variable-width fonts)
     */
    public double getTextWidthPrecise(String text, Font font) {
        return metricsCache.getStringWidthPrecise(font, text);
    }
    
    /**
     * Get font metrics
     */
    public FontMetrics getMetrics(Font font) {
        return metricsCache.getMetrics(font);
    }
    
    /**
     * Get rendering statistics
     */
    public String getStats() {
        GraphicsContextPool.PoolStats poolStats = contextPool.getStats();
        FontMetricsCache.CacheStats cacheStats = metricsCache.getStats();
        
        return String.format("TextRenderer Stats:\n  %s\n  %s", 
            poolStats, cacheStats);
    }
    
    /**
     * Clear all caches and pools
     */
    public void clearAll() {
        contextPool.clearPool();
        metricsCache.clearAll();
    }
    
    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        contextPool.shutdown();
        metricsCache.shutdown();
    }
}