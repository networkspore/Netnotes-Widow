package io.netnotes.gui.fx.display;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global singleton cache for font metrics and character width calculations.
 * Thread-safe and optimized for reuse across multiple text field instances.
 */
public class FontMetricsCache {
    
    private static final FontMetricsCache INSTANCE = new FontMetricsCache();
    
    // Maximum cache sizes to prevent unbounded growth
    private static final int MAX_FONT_CACHE_SIZE = 50;
    private static final int MAX_CHAR_CACHE_SIZE_PER_FONT = 2000;
    
    // Cache structure: Font -> (Character -> Width)
    private final Map<FontKey, FontMetricsData> fontCache = new ConcurrentHashMap<>();
    
    // Shared graphics context for font metrics calculation
    private final BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D dummyGraphics;
    
    /**
     * Inner class to uniquely identify fonts
     */
    private static class FontKey {
        private final String name;
        private final int style;
        private final int size;
        private final int hashCode;
        
        FontKey(Font font) {
            this.name = font.getName();
            this.style = font.getStyle();
            this.size = font.getSize();
            this.hashCode = computeHashCode();
        }
        
        private int computeHashCode() {
            int result = name.hashCode();
            result = 31 * result + style;
            result = 31 * result + size;
            return result;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FontKey)) return false;
            FontKey fontKey = (FontKey) o;
            return style == fontKey.style && 
                   size == fontKey.size && 
                   name.equals(fontKey.name);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    /**
     * Container for font metrics and character width cache
     */
    private static class FontMetricsData {
        final FontMetrics metrics;
        final Map<String, Integer> charWidths = new ConcurrentHashMap<>();
        
        FontMetricsData(FontMetrics metrics) {
            this.metrics = metrics;
        }
        
        int getCharWidth(String ch) {
            // Check cache first
            Integer cached = charWidths.get(ch);
            if (cached != null) {
                return cached;
            }
            
            // Calculate and cache if under size limit
            int width = metrics.stringWidth(ch);
            if (charWidths.size() < MAX_CHAR_CACHE_SIZE_PER_FONT) {
                charWidths.put(ch, width);
            }
            
            return width;
        }
    }
    
    private FontMetricsCache() {
        dummyGraphics = dummyImage.createGraphics();
        // Set rendering hints for consistency
        dummyGraphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        dummyGraphics.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    
    /**
     * Get the singleton instance
     */
    public static FontMetricsCache getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get FontMetrics for a given font
     */
    public FontMetrics getMetrics(Font font) {
        if (font == null) {
            throw new IllegalArgumentException("Font cannot be null");
        }
        
        FontKey key = new FontKey(font);
        FontMetricsData data = fontCache.get(key);
        
        if (data == null) {
            // Check cache size limit
            if (fontCache.size() >= MAX_FONT_CACHE_SIZE) {
                // Remove oldest entry (simple approach - could use LRU)
                fontCache.remove(fontCache.keySet().iterator().next());
            }
            
            // Create new metrics
            synchronized (dummyGraphics) {
                dummyGraphics.setFont(font);
                FontMetrics metrics = dummyGraphics.getFontMetrics();
                data = new FontMetricsData(metrics);
                fontCache.put(key, data);
            }
        }
        
        return data.metrics;
    }
    
    /**
     * Get width of a character string with caching
     */
    public int getCharWidth(Font font, String ch) {
        if (font == null || ch == null || ch.isEmpty()) {
            return 0;
        }
        
        FontKey key = new FontKey(font);
        FontMetricsData data = fontCache.get(key);
        
        if (data == null) {
            // Get metrics first (will create cache entry)
            getMetrics(font);
            data = fontCache.get(key);
        }
        
        return data.getCharWidth(ch);
    }
    
    /**
     * Get width of a string with caching for individual characters.
     * Properly handles surrogate pairs (emoji).
     */
    public int getStringWidth(Font font, String str) {
        if (font == null || str == null || str.isEmpty()) {
            return 0;
        }
        
        // For strings with special characters or long strings, use direct calculation
        if (str.length() > 50 || containsSpecialCharacters(str)) {
            FontMetrics metrics = getMetrics(font);
            return metrics.stringWidth(str);
        }
        
        // Use cached character widths
        int totalWidth = 0;
        int offset = 0;
        int length = str.length();
        
        while (offset < length) {
            int codePoint = str.codePointAt(offset);
            int charCount = Character.charCount(codePoint);
            String ch = str.substring(offset, offset + charCount);
            totalWidth += getCharWidth(font, ch);
            offset += charCount;
        }
        
        return totalWidth;
    }
    
    /**
     * Get precise string bounds using getStringBounds for variable-width fonts
     */
    public double getStringWidthPrecise(Font font, String str) {
        if (font == null || str == null || str.isEmpty()) {
            return 0;
        }
        
        FontMetrics metrics = getMetrics(font);
        return metrics.getStringBounds(str, null).getWidth();
    }
    
    /**
     * Check if string contains emoji or special Unicode characters
     */
    private boolean containsSpecialCharacters(String str) {
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            // Emoji and other special Unicode ranges
            if (ch > 127 && (ch > 0xD7FF || ch < 0x20)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Clear all caches (useful for memory management or testing)
     */
    public void clearAll() {
        fontCache.clear();
    }
    
    /**
     * Clear cache for a specific font
     */
    public void clearFont(Font font) {
        if (font != null) {
            fontCache.remove(new FontKey(font));
        }
    }
    
    /**
     * Get current cache statistics
     */
    public CacheStats getStats() {
        int totalChars = 0;
        for (FontMetricsData data : fontCache.values()) {
            totalChars += data.charWidths.size();
        }
        return new CacheStats(fontCache.size(), totalChars);
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int fontCount;
        public final int totalCharCount;
        
        CacheStats(int fontCount, int totalCharCount) {
            this.fontCount = fontCount;
            this.totalCharCount = totalCharCount;
        }
        
        @Override
        public String toString() {
            return String.format("FontMetricsCache[fonts=%d, chars=%d]", 
                fontCount, totalCharCount);
        }
    }
    
    /**
     * Cleanup resources on shutdown
     */
    public void shutdown() {
        if (dummyGraphics != null) {
            dummyGraphics.dispose();
        }
        clearAll();
    }
}