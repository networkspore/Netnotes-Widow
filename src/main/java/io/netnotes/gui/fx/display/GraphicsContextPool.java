package io.netnotes.gui.fx.display;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global singleton pool for reusable Graphics2D contexts and BufferedImages.
 * Limits the number of active graphics contexts and reuses them efficiently.
 */
public class GraphicsContextPool {
    
    private static final GraphicsContextPool INSTANCE = new GraphicsContextPool();
    
    // Configuration
    private static final int MAX_CONTEXTS = 8; // Maximum concurrent graphics contexts
    private static final int MAX_POOL_SIZE_PER_DIMENSION = 4; // Max cached contexts per size
    
    public static final int SIZE_TOLERANCE = 50; // Pixels tolerance for reusing contexts
    
    // Active context tracking
    private final AtomicInteger activeContexts = new AtomicInteger(0);
    
    // Pool structure: DimensionKey -> Queue of available contexts
    private final ConcurrentHashMap<DimensionKey, ConcurrentLinkedQueue<GraphicsContext>> pool = 
        new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalReused = new AtomicInteger(0);
    private final AtomicInteger totalReleased = new AtomicInteger(0);
    
    /**
     * Key for dimension-based pooling with tolerance
     */
    private static class DimensionKey {
        final int width;
        final int height;
        final int hashCode;
        
        DimensionKey(int width, int height) {
            // Round to nearest SIZE_TOLERANCE to allow reuse
            this.width = ((width + SIZE_TOLERANCE - 1) / SIZE_TOLERANCE) * SIZE_TOLERANCE;
            this.height = ((height + SIZE_TOLERANCE - 1) / SIZE_TOLERANCE) * SIZE_TOLERANCE;
            this.hashCode = computeHashCode();
        }
        
        private int computeHashCode() {
            return 31 * width + height;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DimensionKey)) return false;
            DimensionKey that = (DimensionKey) o;
            return width == that.width && height == that.height;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public String toString() {
            return width + "x" + height;
        }
    }
    
    /**
     * Reusable graphics context with BufferedImage and Graphics2D
     */
    public static class GraphicsContext implements AutoCloseable {
        private final GraphicsContextPool pool;
        private final DimensionKey dimensionKey;
        
        private BufferedImage image;
        private Graphics2D g2d;
        private boolean active;
        
        private GraphicsContext(GraphicsContextPool pool, int width, int height) {
            this.pool = pool;
            this.dimensionKey = new DimensionKey(width, height);
            this.image = new BufferedImage(
                dimensionKey.width, 
                dimensionKey.height, 
                BufferedImage.TYPE_INT_ARGB);
            this.g2d = image.createGraphics();
            setupGraphics(g2d);
            this.active = true;
        }
        
        /**
         * Setup standard rendering hints
         */
        private void setupGraphics(Graphics2D g2d) {
            g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }
        
        /**
         * Get the BufferedImage
         */
        public BufferedImage getImage() {
            return image;
        }
        
        /**
         * Get the Graphics2D context
         */
        public Graphics2D getGraphics() {
            return g2d;
        }
        
        /**
         * Get actual width of the image
         */
        public int getWidth() {
            return image.getWidth();
        }
        
        /**
         * Get actual height of the image
         */
        public int getHeight() {
            return image.getHeight();
        }
        
        /**
         * Clear the image
         */
        public void clear() {
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.setComposite(AlphaComposite.SrcOver);
        }
        
        /**
         * Check if this context is still usable
         */
        private boolean isValid() {
            return image != null && g2d != null && active;
        }
        
        /**
         * Prepare for reuse
         */
        private void reset() {
            if (g2d != null) {
                clear();
                setupGraphics(g2d);
            }
        }
        
        /**
         * Dispose resources (called when removing from pool)
         */
        private void dispose() {
            active = false;
            if (g2d != null) {
                g2d.dispose();
                g2d = null;
            }
            image = null;
        }
        
        /**
         * Release context back to pool (AutoCloseable)
         */
        @Override
        public void close() {
            if (active && pool != null) {
                pool.release(this);
            }
        }
    }
    
    private GraphicsContextPool() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance
     */
    public static GraphicsContextPool getInstance() {
        return INSTANCE;
    }
    
    /**
     * Acquire a graphics context for the given dimensions
     * This method will block if MAX_CONTEXTS is reached
     */
    public GraphicsContext acquire(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        
        DimensionKey key = new DimensionKey(width, height);
        
        // Try to get from pool first
        ConcurrentLinkedQueue<GraphicsContext> queue = pool.get(key);
        if (queue != null) {
            GraphicsContext context = queue.poll();
            if (context != null && context.isValid()) {
                context.reset();
                activeContexts.incrementAndGet();
                totalReused.incrementAndGet();
                return context;
            }
        }
        
        // Wait if we've hit the limit
        while (activeContexts.get() >= MAX_CONTEXTS) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for graphics context", e);
            }
        }
        
        // Create new context
        GraphicsContext context = new GraphicsContext(this, width, height);
        activeContexts.incrementAndGet();
        totalCreated.incrementAndGet();
        return context;
    }
    
    /**
     * Acquire with try-with-resources pattern
     * Example: try (var ctx = pool.acquire(100, 100)) { ... }
     */
    public GraphicsContext acquireContext(int width, int height) {
        return acquire(width, height);
    }
    
    /**
     * Release a context back to the pool
     */
    private void release(GraphicsContext context) {
        if (context == null || !context.isValid()) {
            activeContexts.decrementAndGet();
            return;
        }
        
        DimensionKey key = context.dimensionKey;
        ConcurrentLinkedQueue<GraphicsContext> queue = pool.computeIfAbsent(
            key, _ -> new ConcurrentLinkedQueue<>());
        
        // Only return to pool if under size limit
        if (queue.size() < MAX_POOL_SIZE_PER_DIMENSION) {
            context.reset();
            queue.offer(context);
            totalReleased.incrementAndGet();
        } else {
            // Pool full for this size, dispose it
            context.dispose();
        }
        
        activeContexts.decrementAndGet();
    }
    
    /**
     * Get current pool statistics
     */
    public PoolStats getStats() {
        int pooledContexts = 0;
        for (ConcurrentLinkedQueue<GraphicsContext> queue : pool.values()) {
            pooledContexts += queue.size();
        }
        
        return new PoolStats(
            activeContexts.get(),
            pooledContexts,
            pool.size(),
            totalCreated.get(),
            totalReused.get(),
            totalReleased.get()
        );
    }
    
    /**
     * Pool statistics
     */
    public static class PoolStats {
        public final int activeContexts;
        public final int pooledContexts;
        public final int uniqueSizes;
        public final int totalCreated;
        public final int totalReused;
        public final int totalReleased;
        
        PoolStats(int activeContexts, int pooledContexts, int uniqueSizes,
                 int totalCreated, int totalReused, int totalReleased) {
            this.activeContexts = activeContexts;
            this.pooledContexts = pooledContexts;
            this.uniqueSizes = uniqueSizes;
            this.totalCreated = totalCreated;
            this.totalReused = totalReused;
            this.totalReleased = totalReleased;
        }
        
        public double getReuseRate() {
            int total = totalCreated + totalReused;
            return total > 0 ? (double) totalReused / total * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "GraphicsContextPool[active=%d, pooled=%d, sizes=%d, created=%d, reused=%d, released=%d, reuse=%.1f%%]",
                activeContexts, pooledContexts, uniqueSizes, totalCreated, totalReused, totalReleased, getReuseRate()
            );
        }
    }
    
    /**
     * Clear all pooled contexts (for memory management)
     */
    public void clearPool() {
        for (ConcurrentLinkedQueue<GraphicsContext> queue : pool.values()) {
            GraphicsContext context;
            while ((context = queue.poll()) != null) {
                context.dispose();
            }
        }
        pool.clear();
    }
    
    /**
     * Clear contexts for a specific size
     */
    public void clearSize(int width, int height) {
        DimensionKey key = new DimensionKey(width, height);
        ConcurrentLinkedQueue<GraphicsContext> queue = pool.remove(key);
        if (queue != null) {
            GraphicsContext context;
            while ((context = queue.poll()) != null) {
                context.dispose();
            }
        }
    }
    
    /**
     * Shutdown and cleanup all resources
     */
    public void shutdown() {
        clearPool();
    }
}