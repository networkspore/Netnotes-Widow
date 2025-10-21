package io.netnotes.gui.fx.components.layout;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.utils.FreeMemory;
import io.netnotes.engine.utils.shell.ShellHelpers;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils.ScalingAlgorithm;
import io.netnotes.gui.fx.noteBytes.NoteBytesImage;
import io.netnotes.gui.fx.utils.TaskUtils;

/**
 * Memory-aware resource manager for BufferedLayoutArea caching.
 * 
 * Features:
 * - Adaptive memory management based on system memory
 * - Separate caches for original and scaled images
 * - Image scaling with configurable algorithms
 * - LRU eviction with memory pressure handling
 * - Error conditions for low memory situations
 * - Thread-safe for concurrent access
 */
public class LayoutResourceManager {

    public static final long MAX_MEMORY = 1024 * 1024 * 1024;
    
    // ========== Configuration ==========
    
    private static final int DEFAULT_IMAGE_CACHE_SIZE = 50;
    private static final int DEFAULT_SCALED_IMAGE_CACHE_SIZE = 100;
    private static final int DEFAULT_LAYOUT_CACHE_SIZE = 100;
    
    // Memory thresholds as percentages of available memory
    private static final double DEFAULT_MAX_MEMORY_PERCENT = 0.75; // Use up to 75% of available
    private static final double WARNING_THRESHOLD = 0.80; // Warn at 80% usage
    private static final double CRITICAL_THRESHOLD = 0.95; // Critical at 95% usage
    private static final double AGGRESSIVE_TRIM_THRESHOLD = 0.85; // Trim at 85%
    
    // ========== Memory State ==========
    
    private volatile long maxMemoryBytes;
    private volatile long currentMemoryUsage = 0;
    private volatile MemoryPressure memoryPressure = MemoryPressure.NORMAL;
    private volatile long lastMemoryCheck = 0;
    private static final long MEMORY_CHECK_INTERVAL_MS = 5000; // Check every 5 seconds
    
    // ========== Caches ==========
    
    private final LRUCache<String, CachedImage> imageCache;
    private final LRUCache<String, CachedImage> scaledImageCache;
    private final LRUCache<String, LayoutEngine.LayoutResult> layoutCache;
    
    // ========== Statistics ==========
    
    private long imageCacheHits = 0;
    private long imageCacheMisses = 0;
    private long scaledImageCacheHits = 0;
    private long scaledImageCacheMisses = 0;
    private long layoutCacheHits = 0;
    private long layoutCacheMisses = 0;
    private long memoryWarnings = 0;
    private long memoryErrors = 0;
    
    // ========== Error Listener ==========
    
    private MemoryErrorListener errorListener;
    
    // ========== Enums ==========
    
    /**
     * Memory pressure levels
     */
    public enum MemoryPressure {
        NORMAL,      // < 80% usage
        WARNING,     // 80-95% usage
        CRITICAL     // > 95% usage
    }
    
    /**
     * Memory error types
     */
    public enum MemoryErrorType {
        LOW_MEMORY_WARNING,
        LOW_MEMORY_CRITICAL,
        ALLOCATION_FAILED,
        SYSTEM_MEMORY_LOW
    }
    
    // ========== Interfaces ==========
    
    /**
     * Listener for memory-related errors and warnings
     */
    public interface MemoryErrorListener {
        void onMemoryError(MemoryErrorType type, String message, long currentUsage, long maxMemory);
    }
    
    // ========== Inner Classes ==========
    
    /**
     * Cached image with metadata
     */
    private static class CachedImage {
        BufferedImage image;
        long memorySize;
        long lastAccessTime;
       
        CachedImage(BufferedImage image) {
            this.image = image;
            this.memorySize = calculateImageMemorySize(image);
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        private static long calculateImageMemorySize(BufferedImage image) {
            if (image == null) return 0;
            int width = image.getWidth();
            int height = image.getHeight();
            int colorModel = image.getColorModel().getPixelSize();
            return (long) width * height * (colorModel / 8);
        }

    }
    
    /**
     * Simple LRU cache implementation
     */
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;
        private RemovalListener<K, V> removalListener;
        
        LRUCache(int maxSize) {
            super(maxSize + 1, 0.75f, true);
            this.maxSize = maxSize;
        }
        
        void setRemovalListener(RemovalListener<K, V> listener) {
            this.removalListener = listener;
        }
        
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean shouldRemove = size() > maxSize;
            if (shouldRemove && removalListener != null) {
                removalListener.onRemoval(eldest.getKey(), eldest.getValue());
            }
            return shouldRemove;
        }
        
        interface RemovalListener<K, V> {
            void onRemoval(K key, V value);
        }
    }
    
    // ========== Constructors ==========
    
    public LayoutResourceManager() {
        this(DEFAULT_IMAGE_CACHE_SIZE, DEFAULT_SCALED_IMAGE_CACHE_SIZE, DEFAULT_LAYOUT_CACHE_SIZE);
    }
    
    public LayoutResourceManager(int imageCacheSize, int scaledImageCacheSize, int layoutCacheSize) {
        this.imageCache = new LRUCache<>(imageCacheSize);
        this.imageCache.setRemovalListener((_, value) -> {
            currentMemoryUsage -= value.memorySize;
        });
        
        this.scaledImageCache = new LRUCache<>(scaledImageCacheSize);
        this.scaledImageCache.setRemovalListener((_, value) -> {
            currentMemoryUsage -= value.memorySize;
        });
        
        this.layoutCache = new LRUCache<>(layoutCacheSize);
        
        // Initialize with adaptive memory limit
        updateMemoryLimitFromSystem();
    }
    
    // ========== Memory Management ==========
    
    /**
     * Update memory limit based on current system memory
     */
    public void updateMemoryLimitFromSystem() {
        CompletableFuture<FreeMemory> future = ShellHelpers.getFreeMemory(TaskUtils.getVirtualExecutor());
        
        future.thenAccept(freeMemory -> {
            if (freeMemory != null) {
                long availableKB = freeMemory.getMemAvailableKB();
                //long totalKB = freeMemory.getMemTotalKB();
                
                // Use percentage of available memory
                long availableBytes = availableKB * 1024;
                long newLimit = (long) (availableBytes * DEFAULT_MAX_MEMORY_PERCENT);
                
                // Ensure minimum of 50MB and maximum of 2GB
                newLimit = Math.max(50 * 1024 * 1024, newLimit);
                newLimit = Math.min(2L * 1024 * 1024 * 1024, newLimit);
                
                maxMemoryBytes = newLimit;
                
                // Check if we need to trim
                updateMemoryPressure();
                
            } else {
                // Fallback to default
                maxMemoryBytes = MAX_MEMORY;
            }
        }).exceptionally(_ -> {
            // Error getting system memory, default
            maxMemoryBytes = MAX_MEMORY;
            return null;
        });
    }
    
    /**
     * Check system memory and update pressure state
     */
    private void checkSystemMemory() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCheck < MEMORY_CHECK_INTERVAL_MS) {
            return; // Too soon since last check
        }
        lastMemoryCheck = now;
        
        CompletableFuture<FreeMemory> future = ShellHelpers.getFreeMemory(TaskUtils.getVirtualExecutor());
        
        future.thenAccept(freeMemory -> {
            if (freeMemory != null) {
                long availableKB = freeMemory.getMemAvailableKB();
                long totalKB = freeMemory.getMemTotalKB();
                
                double availablePercent = (availableKB * 100.0) / totalKB;
                
                // System is running low on memory
                if (availablePercent < 10.0) {
                    fireMemoryError(MemoryErrorType.SYSTEM_MEMORY_LOW,
                        String.format("System memory low: %.1f%% available", availablePercent),
                        currentMemoryUsage, maxMemoryBytes);
                    
                    // Aggressively trim our caches
                    aggressiveTrim();
                }
            }
        });
    }
    
    /**
     * Update memory pressure state based on current usage
     */
    private synchronized void updateMemoryPressure() {
        double usagePercent = (currentMemoryUsage * 100.0) / maxMemoryBytes;
        MemoryPressure oldPressure = memoryPressure;
        
        if (usagePercent >= CRITICAL_THRESHOLD * 100) {
            memoryPressure = MemoryPressure.CRITICAL;
            if (oldPressure != MemoryPressure.CRITICAL) {
                memoryErrors++;
                fireMemoryError(MemoryErrorType.LOW_MEMORY_CRITICAL,
                    String.format("Critical memory pressure: %.1f%% used", usagePercent),
                    currentMemoryUsage, maxMemoryBytes);
            }
        } else if (usagePercent >= WARNING_THRESHOLD * 100) {
            memoryPressure = MemoryPressure.WARNING;
            if (oldPressure == MemoryPressure.NORMAL) {
                memoryWarnings++;
                fireMemoryError(MemoryErrorType.LOW_MEMORY_WARNING,
                    String.format("Memory pressure warning: %.1f%% used", usagePercent),
                    currentMemoryUsage, maxMemoryBytes);
            }
        } else {
            memoryPressure = MemoryPressure.NORMAL;
        }
        
        // Auto-trim if above threshold
        if (usagePercent >= AGGRESSIVE_TRIM_THRESHOLD * 100) {
            aggressiveTrim();
        }
    }
    
    /**
     * Fire memory error to listener
     */
    private void fireMemoryError(MemoryErrorType type, String message, long currentUsage, long maxMemory) {
        if (errorListener != null) {
            try {
                errorListener.onMemoryError(type, message, currentUsage, maxMemory);
            } catch (Exception e) {
                System.err.println("Error in memory error listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if we can allocate memory for new entry
     */
    private synchronized boolean canAllocate(long requiredBytes) {
        if (currentMemoryUsage + requiredBytes <= maxMemoryBytes) {
            return true;
        }
        
        // Try to free memory
        evictLeastRecentlyUsedImages(requiredBytes);
        
        return currentMemoryUsage + requiredBytes <= maxMemoryBytes;
    }
    
    // ========== Original Image Caching ==========
    
    /**
     * Get cached image or decode and cache
     */
    public synchronized BufferedImage getImage(NoteBytesImage imageData) throws IOException {
        checkSystemMemory();
        
        String key = generateImageKey(imageData);
        
        CachedImage cached = imageCache.get(key);
        if (cached != null) {
            cached.updateAccessTime();
            imageCacheHits++;
            return cached.image;
        }
        
        // Cache miss - decode image
        imageCacheMisses++;
        BufferedImage image = imageData.getAsBufferedImage(false);
        
        if (image != null) {
            long imageSize = CachedImage.calculateImageMemorySize(image);
            
            if (!canAllocate(imageSize)) {
                fireMemoryError(MemoryErrorType.ALLOCATION_FAILED,
                    "Cannot allocate memory for image: " + imageSize + " bytes",
                    currentMemoryUsage, maxMemoryBytes);
                // Return image without caching
                return image;
            }
            
            CachedImage cachedImage = new CachedImage(image);
            CachedImage old = imageCache.put(key, cachedImage);
            
            if (old != null) {
                currentMemoryUsage -= old.memorySize;
            }
            currentMemoryUsage += cachedImage.memorySize;
            
            updateMemoryPressure();
        }
        
        return image;
    }
    
    // ========== Scaled Image Caching ==========
    
    /**
     * Get or create scaled image with specified algorithm
     */
    public synchronized BufferedImage getScaledImage(NoteBytesImage imageContent, int width, int height, 
        ScalingAlgorithm algorithm) throws IOException 
    {
        checkSystemMemory();
        
        String cacheKey = generateScaledImageKey(imageContent, width, height, algorithm);
        
        CachedImage cached = scaledImageCache.get(cacheKey);
        if (cached != null) {
            cached.updateAccessTime();
            scaledImageCacheHits++;
            return cached.image;
        }
        
        // Cache miss - need to scale
        scaledImageCacheMisses++;
        
        // Get original image
        BufferedImage original = getImage(imageContent);
        if (original == null) {
            return null;
        }
        
        // Don't scale if already correct size
        if (original.getWidth() == width && original.getHeight() == height) {
            return original;
        }
        
        // Scale the image
        BufferedImage scaled = ScalingUtils.scaleImage(original, width, height, algorithm);
        
        if (scaled != null) {
            long scaledSize = CachedImage.calculateImageMemorySize(scaled);
            
            if (!canAllocate(scaledSize)) {
                fireMemoryError(MemoryErrorType.ALLOCATION_FAILED,
                    "Cannot allocate memory for scaled image: " + scaledSize + " bytes",
                    currentMemoryUsage, maxMemoryBytes);
                // Return scaled image without caching
                return scaled;
            }
            
            CachedImage cachedScaled = new CachedImage(scaled);
            CachedImage old = scaledImageCache.put(cacheKey, cachedScaled);
            
            if (old != null) {
                currentMemoryUsage -= old.memorySize;
            }
            currentMemoryUsage += cachedScaled.memorySize;
            
            updateMemoryPressure();
        }
        
        return scaled;
    }
    

    // ========== Layout Caching ==========
    
    public synchronized LayoutEngine.LayoutResult getLayout(String segmentId) {
        LayoutEngine.LayoutResult result = layoutCache.get(segmentId);
        if (result != null) {
            layoutCacheHits++;
        } else {
            layoutCacheMisses++;
        }
        return result;
    }
    
    public synchronized void cacheLayout(String segmentId, LayoutEngine.LayoutResult result) {
        if (result == null) return;
        layoutCache.put(segmentId, result);
    }
    
    public synchronized void clearLayoutCache() {
        layoutCache.clear();
    }
    
    // ========== Key Generation ==========
    
    private String generateImageKey(NoteBytesImage imageData) {
        return Integer.toHexString(imageData.hashCode());
    }
    
    private String generateScaledImageKey(NoteBytesImage image, int width, int height, ScalingAlgorithm algorithm) {
        String imageHash = generateImageKey(image);
        return imageHash + "_" + width + "x" + height + "_" + algorithm.getValue();
    }
    
    // ========== Memory Eviction ==========
    
    /**
     * Evict least recently used images to free memory
     */
    private void evictLeastRecentlyUsedImages(long requiredBytes) {
        long freedMemory = 0;
        
        // First try scaled images (they can be regenerated)
        java.util.List<Map.Entry<String, CachedImage>> scaledEntries = 
            new java.util.ArrayList<>(scaledImageCache.entrySet());
        
        scaledEntries.sort((e1, e2) -> 
            Long.compare(e1.getValue().lastAccessTime, e2.getValue().lastAccessTime));
        
        for (Map.Entry<String, CachedImage> entry : scaledEntries) {
            if (freedMemory >= requiredBytes) break;
            
            CachedImage cached = scaledImageCache.remove(entry.getKey());
            if (cached != null) {
                freedMemory += cached.memorySize;
                currentMemoryUsage -= cached.memorySize;
            }
        }
        
        // If still need more, evict original images
        if (freedMemory < requiredBytes) {
            java.util.List<Map.Entry<String, CachedImage>> imageEntries = 
                new java.util.ArrayList<>(imageCache.entrySet());
            
            imageEntries.sort((e1, e2) -> 
                Long.compare(e1.getValue().lastAccessTime, e2.getValue().lastAccessTime));
            
            for (Map.Entry<String, CachedImage> entry : imageEntries) {
                if (freedMemory >= requiredBytes) break;
                
                CachedImage cached = imageCache.remove(entry.getKey());
                if (cached != null) {
                    freedMemory += cached.memorySize;
                    currentMemoryUsage -= cached.memorySize;
                }
            }
        }
    }
    
    /**
     * Aggressively free memory by clearing old entries
     */
    public synchronized void aggressiveTrim() {
        long targetMemory = maxMemoryBytes / 2; // Trim to 50%
        if (currentMemoryUsage > targetMemory) {
            evictLeastRecentlyUsedImages(currentMemoryUsage - targetMemory);
        }
    }
    
    // ========== Statistics & Info ==========
    
    public synchronized long getMemoryUsage() {
        return currentMemoryUsage;
    }
    
    public synchronized double getMemoryUsageMB() {
        return currentMemoryUsage / (1024.0 * 1024.0);
    }
    
    public long getMaxMemory() {
        return maxMemoryBytes;
    }
    
    public synchronized double getMemoryUsagePercent() {
        return (currentMemoryUsage * 100.0) / maxMemoryBytes;
    }
    
    public synchronized MemoryPressure getMemoryPressure() {
        return memoryPressure;
    }
    
    public synchronized CacheStats getStats() {
        return new CacheStats(
            imageCache.size(),
            scaledImageCache.size(),
            layoutCache.size(),
            imageCacheHits,
            imageCacheMisses,
            scaledImageCacheHits,
            scaledImageCacheMisses,
            layoutCacheHits,
            layoutCacheMisses,
            currentMemoryUsage,
            maxMemoryBytes,
            memoryPressure,
            memoryWarnings,
            memoryErrors
        );
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStats {
        public final int imageCacheSize;
        public final int scaledImageCacheSize;
        public final int layoutCacheSize;
        public final long imageCacheHits;
        public final long imageCacheMisses;
        public final long scaledImageCacheHits;
        public final long scaledImageCacheMisses;
        public final long layoutCacheHits;
        public final long layoutCacheMisses;
        public final long memoryUsage;
        public final long maxMemory;
        public final MemoryPressure memoryPressure;
        public final long memoryWarnings;
        public final long memoryErrors;
        
        CacheStats(int imageCacheSize, int scaledImageCacheSize, int layoutCacheSize,
                   long imageCacheHits, long imageCacheMisses,
                   long scaledImageCacheHits, long scaledImageCacheMisses,
                   long layoutCacheHits, long layoutCacheMisses,
                   long memoryUsage, long maxMemory,
                   MemoryPressure memoryPressure,
                   long memoryWarnings, long memoryErrors) {
            this.imageCacheSize = imageCacheSize;
            this.scaledImageCacheSize = scaledImageCacheSize;
            this.layoutCacheSize = layoutCacheSize;
            this.imageCacheHits = imageCacheHits;
            this.imageCacheMisses = imageCacheMisses;
            this.scaledImageCacheHits = scaledImageCacheHits;
            this.scaledImageCacheMisses = scaledImageCacheMisses;
            this.layoutCacheHits = layoutCacheHits;
            this.layoutCacheMisses = layoutCacheMisses;
            this.memoryUsage = memoryUsage;
            this.maxMemory = maxMemory;
            this.memoryPressure = memoryPressure;
            this.memoryWarnings = memoryWarnings;
            this.memoryErrors = memoryErrors;
        }
        
        public double getImageHitRate() {
            long total = imageCacheHits + imageCacheMisses;
            return total == 0 ? 0 : (imageCacheHits * 100.0) / total;
        }
        
        public double getScaledImageHitRate() {
            long total = scaledImageCacheHits + scaledImageCacheMisses;
            return total == 0 ? 0 : (scaledImageCacheHits * 100.0) / total;
        }
        
        public double getLayoutHitRate() {
            long total = layoutCacheHits + layoutCacheMisses;
            return total == 0 ? 0 : (layoutCacheHits * 100.0) / total;
        }
        
        public double getMemoryUsagePercent() {
            return (memoryUsage * 100.0) / maxMemory;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats[images=%d, scaled=%d, layouts=%d, imageHit=%.1f%%, scaledHit=%.1f%%, " +
                "layoutHit=%.1f%%, memory=%.1fMB/%.1fMB (%.1f%%), pressure=%s, warnings=%d, errors=%d]",
                imageCacheSize, scaledImageCacheSize, layoutCacheSize,
                getImageHitRate(), getScaledImageHitRate(), getLayoutHitRate(),
                memoryUsage / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                getMemoryUsagePercent(),
                memoryPressure,
                memoryWarnings,
                memoryErrors
            );
        }
    }
    
    // ========== Cleanup ==========
    
    public synchronized void clearAll() {
        imageCache.clear();
        scaledImageCache.clear();
        layoutCache.clear();
        currentMemoryUsage = 0;
        memoryPressure = MemoryPressure.NORMAL;
    }
    
    public synchronized void clearImageCache() {
        imageCache.clear();
        // Recalculate memory usage from scaled cache only
        currentMemoryUsage = scaledImageCache.values().stream()
            .mapToLong(img -> img.memorySize)
            .sum();
        updateMemoryPressure();
    }
    
    public synchronized void clearScaledImageCache() {
        scaledImageCache.clear();
        // Recalculate memory usage from image cache only
        currentMemoryUsage = imageCache.values().stream()
            .mapToLong(img -> img.memorySize)
            .sum();
        updateMemoryPressure();
    }
    
    public void setMemoryErrorListener(MemoryErrorListener listener) {
        this.errorListener = listener;
    }
}