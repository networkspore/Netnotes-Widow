// In BufferedLayoutArea.java or new file GlyphBoundaryCache.java

package io.netnotes.gui.fx.components.layout;

import io.netnotes.gui.fx.display.TextRenderer;

import java.awt.*;
import java.util.Arrays;

/**
 * Caches character-level glyph boundaries for fast cursor positioning.
 * For a text segment with N characters, stores N+1 boundary positions.
 * 
 * Example: "Hello" has 6 boundaries: |H|e|l|l|o|
 *          boundaries = [0, 12, 24, 32, 40, 52]
 */
public class GlyphBoundaryCache {
    private int[] boundaries;  // X-offsets for each character boundary
    private String cachedText; // Text this cache was built for
    private Font cachedFont;   // Font this cache was built for
    private int baseX;         // Base X coordinate (segment start + padding)
    
    /**
     * Build glyph boundaries for a text segment
     */
    public static GlyphBoundaryCache build(
        String text, 
        Font font, 
        int baseX,
        TextRenderer textRenderer
    ) {
        if (text == null || text.isEmpty()) {
            return new GlyphBoundaryCache(new int[]{0}, "", font, baseX);
        }
        
        GlyphBoundaryCache cache = new GlyphBoundaryCache();
        cache.cachedText = text;
        cache.cachedFont = font;
        cache.baseX = baseX;
        cache.boundaries = new int[text.length() + 1];
        
        // First boundary is at start
        cache.boundaries[0] = 0;
        
        // Calculate cumulative widths for each character
        for (int i = 0; i < text.length(); i++) {
            String substring = text.substring(0, i + 1);
            int width = textRenderer.getTextWidth(substring, font);
            cache.boundaries[i + 1] = width;
        }
        
        return cache;
    }
    
    private GlyphBoundaryCache() {}
    
    private GlyphBoundaryCache(int[] boundaries, String text, Font font, int baseX) {
        this.boundaries = boundaries;
        this.cachedText = text;
        this.cachedFont = font;
        this.baseX = baseX;
    }
    
    /**
     * Find character index at given X coordinate (relative to segment start).
     * Uses binary search for O(log n) lookup.
     * 
     * @param relativeX X coordinate relative to text start (subtract baseX first)
     * @return character index (0 to text.length())
     */
    public int findCharacterAt(int relativeX) {
        if (boundaries.length == 0) return 0;
        
        // Clamp to valid range
        if (relativeX <= boundaries[0]) return 0;
        if (relativeX >= boundaries[boundaries.length - 1]) {
            return boundaries.length - 1;
        }
        
        // Binary search for closest boundary
        int left = 0;
        int right = boundaries.length - 1;
        
        while (left < right) {
            int mid = (left + right) / 2;
            int midX = boundaries[mid];
            int nextX = boundaries[mid + 1];
            
            // Check if relativeX is between mid and mid+1
            if (relativeX >= midX && relativeX < nextX) {
                // Snap to closer boundary
                int distToMid = relativeX - midX;
                int distToNext = nextX - relativeX;
                return distToMid < distToNext ? mid : mid + 1;
            }
            
            if (relativeX < midX) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    /**
     * Get X coordinate for character at given index (relative to segment start)
     * O(1) lookup.
     * 
     * @param charIndex character index (0 to text.length())
     * @return X offset from text start
     */
    public int getXForCharacter(int charIndex) {
        if (charIndex < 0 || charIndex >= boundaries.length) {
            return charIndex <= 0 ? boundaries[0] : boundaries[boundaries.length - 1];
        }
        return boundaries[charIndex];
    }
    
    /**
     * Get absolute X coordinate for character (includes baseX)
     */
    public int getAbsoluteXForCharacter(int charIndex) {
        return baseX + getXForCharacter(charIndex);
    }
    
    /**
     * Get width of character at given index
     */
    public int getCharacterWidth(int charIndex) {
        if (charIndex < 0 || charIndex >= boundaries.length - 1) {
            return 0;
        }
        return boundaries[charIndex + 1] - boundaries[charIndex];
    }
    
    /**
     * Check if cache is valid for given text and font
     */
    public boolean isValidFor(String text, Font font) {
        return cachedText.equals(text) && 
               cachedFont.equals(font);
    }
    
    public int getBaseX() {
        return baseX;
    }
    
    public int getCharacterCount() {
        return boundaries.length - 1;
    }
    
    public String getCachedText() {
        return cachedText;
    }
    
    @Override
    public String toString() {
        return String.format("GlyphCache[text='%s', boundaries=%s]", 
            cachedText.substring(0, Math.min(20, cachedText.length())),
            Arrays.toString(Arrays.copyOf(boundaries, Math.min(10, boundaries.length))));
    }
}