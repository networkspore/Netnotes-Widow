package io.netnotes.gui.fx.components.layout;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.netnotes.engine.utils.FreeMemory;
import io.netnotes.engine.utils.shell.ShellHelpers;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.gui.fx.display.ImageHelpers;
import io.netnotes.gui.fx.utils.TaskUtils;

/**
 * Singleton smart resource manager for BufferedLayoutArea.
 * 
 * Philosophy:
 * - Shared across all BufferedLayoutArea instances for maximum efficiency
 * - Uses Blake2b hashing to prevent cross-contamination
 * - Cache everything that's currently in use
 * - Only evict when segments are removed from documents
 * - Monitor system memory for warnings, but don't arbitrarily limit cache
 * - Resources manage their own lifecycle based on document structure
 * 
 * Thread-safe for concurrent access from multiple BufferedLayoutArea instances.
 */
public class LayoutResourceManager {
    
    // ========== Singleton ==========
    
    private static volatile LayoutResourceManager instance;

    public static final int HASH_SIZE = 8;
    public static final long STALE_IMAGE_TIME = 5 * 60 * 1000;
    /**
     * Get the singleton instance
     */
    public static LayoutResourceManager getInstance() {
        return instance;
    }
    
    // ========== Memory Monitoring ==========
    
    private volatile long currentMemoryUsage = 0;
    private volatile MemoryPressure memoryPressure = MemoryPressure.NORMAL;
    private volatile long lastMemoryCheck = 0;
    private static final long MEMORY_CHECK_INTERVAL_MS = 10000; // Check every 10 seconds
    
    // Memory thresholds for warnings only
    private static final double SYSTEM_WARNING_THRESHOLD = 0.10; // Warn when system has < 10% free
    private static final double SYSTEM_CRITICAL_THRESHOLD = 0.05; // Critical when < 5% free
    
    // ========== Caches ==========
    
    // Key: Blake2b hash of image data (8 bytes as hex string)
    private final ConcurrentHashMap<String, CachedImage> imageCache;
    
    // Key: content hash + dimensions (per-instance, not shared)
    private final ConcurrentHashMap<String, CachedLayoutResult> layoutCache;
    
    // Track which images are referenced by which instances
    // Outer key: instance ID, Inner key: image hash
    private final ConcurrentHashMap<String, Set<String>> activeImagesByInstance;
    private final ConcurrentHashMap<String, Set<String>> activeScaledImagesByInstance;
    
    // ========== Statistics ==========
    
    private volatile long layoutCacheHits = 0;
    private volatile long layoutCacheMisses = 0;
    private volatile long memoryWarnings = 0;
    
    // ========== Error Listener ==========
    
    private volatile MemoryErrorListener errorListener;
    
    // ========== Enums ==========
    
    /**
     * Memory pressure levels - for monitoring only
     */
    public enum MemoryPressure {
        NORMAL,      // System has plenty of memory
        WARNING,     // System memory getting low (< 10% free)
        CRITICAL     // System memory very low (< 5% free)
    }
    
    /**
     * Memory warning types
     */
    public enum MemoryWarningType {
        SYSTEM_MEMORY_LOW,
        SYSTEM_MEMORY_CRITICAL
    }
    
    // ========== Interfaces ==========
    
    /**
     * Listener for memory warnings (not errors - we don't fail on memory)
     */
    public interface MemoryErrorListener {
        void onMemoryWarning(MemoryWarningType type, String message, long currentUsage, long systemAvailable);
    }
    
    // ========== Inner Classes ==========
    
    /**
     * Cached image with metadata
     */
    private static class CachedImage {
        final BufferedImage image;
        long memorySize;
        final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
        final Set<String> instanceIds = ConcurrentHashMap.newKeySet();
        
        CachedImage(BufferedImage image) {
            this.image = image;
            this.memorySize = ImageHelpers.getBufferedImageSizeInBytes(image);
        }
        
        boolean addInstance(String instanceId) {
            
            boolean added = instanceIds.add(instanceId);
            if(added){
                updateAccessTime();
            }
            return added;
        }

        boolean removeInstance(String instanceId) {
            boolean removed = instanceIds.remove(instanceId);
            if(removed){
                updateAccessTime();
            }
            return removed;
        }
     
        
        boolean isUnused() {
            return instanceIds.size() == 0;
        }
        
        void updateAccessTime() {
            this.lastAccessTime.set(System.currentTimeMillis());
        }
        
    }
    
    /**
     * Cached layout result with instance tracking
     */
    private static class CachedLayoutResult {
        LayoutEngine.LayoutResult result;
        String instanceId;
        long lastAccessTime;
        
        CachedLayoutResult(LayoutEngine.LayoutResult result, String instanceId) {
            this.result = result;
            this.instanceId = instanceId;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    
    // ========== Constructor ==========
    
    private LayoutResourceManager() {
        this.imageCache = new ConcurrentHashMap<>();
        this.layoutCache = new ConcurrentHashMap<>();
        this.activeImagesByInstance = new ConcurrentHashMap<>();
        this.activeScaledImagesByInstance = new ConcurrentHashMap<>();
        
        // Start background memory monitor
        startMemoryMonitor();
    }
    
    // ========== Instance Management ==========
    
    /**
     * Register a new BufferedLayoutArea instance
     * Returns a unique instance ID for tracking
     */
    public String registerInstance() {
        String instanceId = generateInstanceId();
        activeImagesByInstance.put(instanceId, ConcurrentHashMap.newKeySet());
        activeScaledImagesByInstance.put(instanceId, ConcurrentHashMap.newKeySet());
        return instanceId;
    }
    
    /**
     * Unregister a BufferedLayoutArea instance and clean up its resources
     */
    public void unregisterInstance(String instanceId) {
        if (instanceId == null) return;
        
        // Get images used by this instance
        Set<String> instanceImages = activeImagesByInstance.remove(instanceId);
        
        // Decrement reference counts
        if (instanceImages != null) {
            for (String imageKey : instanceImages) {
                decrementImageRef(instanceId, imageKey);
            }
        }
        
        // Remove layouts for this instance
        layoutCache.entrySet().removeIf(entry -> 
            entry.getValue().instanceId.equals(instanceId));
        
        // Clean up unused resources
        cleanupUnusedImages(instanceId, false);
    }
    
    private String generateInstanceId() {
        return "i" + NoteUUID.createSafeUUID128() + "_" + Thread.currentThread().threadId();
    }
    
    // ========== Memory Monitoring ==========
    
    /**
     * Start background thread to monitor system memory
     */
    private void startMemoryMonitor() {
        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    Thread.sleep(MEMORY_CHECK_INTERVAL_MS);
                    checkSystemMemory();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, TaskUtils.getVirtualExecutor());
    }
    
    /**
     * Check system memory and warn if low (but don't limit cache)
     */
    private void checkSystemMemory() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCheck < MEMORY_CHECK_INTERVAL_MS) {
            return;
        }
        lastMemoryCheck = now;
        
        CompletableFuture<FreeMemory> future = ShellHelpers.getFreeMemory(TaskUtils.getVirtualExecutor());
        
        future.thenAccept(freeMemory -> {
            if (freeMemory != null) {
                long availableKB = freeMemory.getMemAvailableKB();
                long totalKB = freeMemory.getMemTotalKB();
                
                double availablePercent = (availableKB * 100.0) / totalKB;
                MemoryPressure oldPressure = memoryPressure;
                
                if (availablePercent < SYSTEM_CRITICAL_THRESHOLD * 100) {
                    memoryPressure = MemoryPressure.CRITICAL;
                    if (oldPressure != MemoryPressure.CRITICAL) {
                        memoryWarnings++;
                        fireMemoryWarning(MemoryWarningType.SYSTEM_MEMORY_CRITICAL,
                            String.format("System memory critically low: %.1f%% available", availablePercent),
                            currentMemoryUsage, availableKB * 1024);
                    }
                } else if (availablePercent < SYSTEM_WARNING_THRESHOLD * 100) {
                    memoryPressure = MemoryPressure.WARNING;
                    if (oldPressure == MemoryPressure.NORMAL) {
                        memoryWarnings++;
                        fireMemoryWarning(MemoryWarningType.SYSTEM_MEMORY_LOW,
                            String.format("System memory getting low: %.1f%% available", availablePercent),
                            currentMemoryUsage, availableKB * 1024);
                    }
                } else {
                    memoryPressure = MemoryPressure.NORMAL;
                }
            }
        });
    }
    
    /**
     * Fire memory warning to listener
     */
    private void fireMemoryWarning(MemoryWarningType type, String message, long currentUsage, long systemAvailable) {
        MemoryErrorListener listener = errorListener;
        if (listener != null) {
            try {
                listener.onMemoryWarning(type, message, currentUsage, systemAvailable);
            } catch (Exception e) {
                System.err.println("Error in memory warning listener: " + e.getMessage());
            }
        }
    }
    
    // ========== Original Image Caching ==========
    
    public BufferedImage getImage(String hashId, String instanceId) throws IOException {
        CachedImage cached = imageCache.get(hashId);
        if (cached != null) {
            cached.updateAccessTime();
            markImageActive(instanceId, hashId);
            return cached.image;
        }
        return null;
    }

    public boolean addImage(String hashId, BufferedImage image, String instanceId){
      
        if (image != null) {

            CachedImage cachedImage = new CachedImage(image);
            imageCache.put(hashId, cachedImage);
            markImageActive(instanceId, hashId);
            currentMemoryUsage += cachedImage.memorySize;
            return true;
        }
        return false;
    }
    
    private boolean markImageActive(String instanceId, String imageKey) {
        Set<String> instanceImages = activeImagesByInstance.get(instanceId);
        if (instanceImages != null && instanceImages.add(imageKey)) {
            // Newly added - increment reference count
            CachedImage cached = imageCache.get(imageKey);
            if (cached != null) {
                return cached.addInstance(instanceId);
            }
        }
        return false;
    }
    
    private boolean decrementImageRef(String instanceId, String imageKey) {
        CachedImage cached = imageCache.get(imageKey);
        if (cached != null) {
            return cached.removeInstance(instanceId);
        }
        return false;
    }
    

    
    // ========== Layout Caching ==========
    
    public LayoutEngine.LayoutResult getLayout(String layoutKey, String instanceId) {
        String fullKey = instanceId + ":" + layoutKey;
        CachedLayoutResult cached = layoutCache.get(fullKey);
        
        if (cached != null) {
            cached.updateAccessTime();
            layoutCacheHits++;
            return cached.result;
        }
        
        layoutCacheMisses++;
        return null;
    }
    
    public void cacheLayout(String layoutKey, LayoutEngine.LayoutResult result, String instanceId) {
        if (result == null || instanceId == null) return;
        
        String fullKey = instanceId + ":" + layoutKey;
        layoutCache.put(fullKey, new CachedLayoutResult(result, instanceId));
    }
    
    // ========== Lifecycle Management ==========
    
    /**
     * Called when document is cleared in an instance
     */
    public void onDocumentCleared(String instanceId) {
        if (instanceId == null) return;
        
        // Get current images for this instance
        Set<String> instanceImages = activeImagesByInstance.get(instanceId);
        
        // Decrement all references
        if (instanceImages != null) {
            for (String imageKey : instanceImages) {
                decrementImageRef(instanceId, imageKey);
            }
            instanceImages.clear();
        }
    
        // Clear layouts for this instance
        layoutCache.entrySet().removeIf(entry -> 
            entry.getValue().instanceId.equals(instanceId));
        
        // Clean up unused resources
        cleanupUnusedImages(true);
    }
    
    /**
     * Called when layout is invalidated (e.g., window resize)
     */
    public void onLayoutInvalidated(String instanceId) {
        if (instanceId == null) return;
        
        // Clear layouts for this instance only
        layoutCache.entrySet().removeIf(entry -> 
            entry.getValue().instanceId.equals(instanceId));
    }
    
    /**
     * Mark the start of a new render cycle for an instance
     */
    public void beginRenderCycle(String instanceId) {
        if (instanceId == null) return;
        
        // Get current references
        Set<String> instanceImages = activeImagesByInstance.get(instanceId);
        
        // Decrement old references
        if (instanceImages != null) {
            for (String imageKey : instanceImages) {
                decrementImageRef(instanceId, imageKey);
            }
            instanceImages.clear();
        }
        
    }
    
    /**
     * Mark the end of a render cycle
     */
    public void endRenderCycle(String instanceId) {
        // Periodically clean up unused resources
        cleanupUnusedImages(true);
    }
    
    private boolean isStaleImage(long lastAccessed){
        long currentTime = System.currentTimeMillis();
        return currentTime - lastAccessed > STALE_IMAGE_TIME; 
    }

    private void cleanupUnusedImages(boolean checkStale) {
        cleanupUnusedImages(null, checkStale);
    }
    /**
     * Remove images that are no longer referenced by any instance
     */
    private void cleanupUnusedImages(String instanceId, boolean checkStale) {
        // Remove unreferenced original images
        imageCache.entrySet().removeIf(entry -> {
            CachedImage cachedImage = entry.getValue();
            
            if (cachedImage.isUnused() && checkStale ? isStaleImage(cachedImage.lastAccessTime.get()) : true ) {
                currentMemoryUsage -= entry.getValue().memorySize;
                return true;
            }
            return false;
        });
    }
    
    
    // ========== Statistics & Info ==========
    
    public long getMemoryUsage() {
        return currentMemoryUsage;
    }
    
    public double getMemoryUsageMB() {
        return currentMemoryUsage / (1024.0 * 1024.0);
    }
    
    public MemoryPressure getMemoryPressure() {
        return memoryPressure;
    }
    
    public int getActiveInstanceCount() {
        return activeImagesByInstance.size();
    }
    
    public CacheStats getStats() {
        return new CacheStats(
            imageCache.size(),
            layoutCache.size(),
            layoutCacheHits,
            layoutCacheMisses,
            currentMemoryUsage,
            memoryPressure,
            memoryWarnings,
            getActiveInstanceCount()
        );
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStats {
        public final int imageCacheSize;
        public final int layoutCacheSize;
        public final long layoutCacheHits;
        public final long layoutCacheMisses;
        public final long memoryUsage;
        public final MemoryPressure memoryPressure;
        public final long memoryWarnings;
        public final int activeInstances;
        
        CacheStats(int imageCacheSize, int layoutCacheSize,
                   long layoutCacheHits, long layoutCacheMisses,
                   long memoryUsage, MemoryPressure memoryPressure,
                   long memoryWarnings, int activeInstances) {
            this.imageCacheSize = imageCacheSize;
            this.layoutCacheSize = layoutCacheSize;
            this.layoutCacheHits = layoutCacheHits;
            this.layoutCacheMisses = layoutCacheMisses;
            this.memoryUsage = memoryUsage;
            this.memoryPressure = memoryPressure;
            this.memoryWarnings = memoryWarnings;
            this.activeInstances = activeInstances;
        }
        
  
        public double getLayoutHitRate() {
            long total = layoutCacheHits + layoutCacheMisses;
            return total == 0 ? 0 : (layoutCacheHits * 100.0) / total;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats[instances=%d, images=%d,  layouts=%d, " +
                "layoutHit=%.1f%%, " +
                "memory=%.1fMB, pressure=%s, warnings=%d]",
                activeInstances, imageCacheSize, layoutCacheSize,
                getLayoutHitRate(),
                memoryUsage / (1024.0 * 1024.0),
                memoryPressure,
                memoryWarnings
            );
        }
    }
    
    // ========== Cleanup ==========
    
    /**
     * Clear all caches (use with caution - affects all instances)
     */
    public void clearAll() {
        imageCache.clear();
        layoutCache.clear();
        activeImagesByInstance.values().forEach(Set::clear);
        activeScaledImagesByInstance.values().forEach(Set::clear);
        currentMemoryUsage = 0;
        memoryPressure = MemoryPressure.NORMAL;
    }
    
    public void setMemoryErrorListener(MemoryErrorListener listener) {
        this.errorListener = listener;
    }
}