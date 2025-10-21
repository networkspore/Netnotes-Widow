package io.netnotes.gui.fx.components.images;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.components.images.effects.ImageEffects;
import io.netnotes.gui.fx.utils.TaskUtils;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils.ScalingAlgorithm;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Canvas-based image rendering component with BufferedImage backing.
 * Supports both image transformation and direct rendering modes with effects.
 */
public abstract class BufferedCanvasView extends Canvas {
    

    public enum RenderMode {
        TRANSFORM,  // Apply effects to existing image
        GENERATE,   // Generate image from scratch
        HYBRID      // Generate base, then apply effects
    }
    
    public enum FitMode {
        NONE,       // No scaling, render at native size
        FIT_WIDTH,  // Scale to fit width, maintain aspect ratio
        FIT_HEIGHT, // Scale to fit height, maintain aspect ratio
        FIT_SIZE    // Scale to fit both width and height (may distort)
    }
    
    private RenderMode m_renderMode = RenderMode.GENERATE;
    private FitMode m_fitMode = FitMode.NONE;
    private ScalingAlgorithm m_scalingAlgorithm = ScalingAlgorithm.BILINEAR;
    
    // Reusable rendering resources
    private BufferedImage m_workingBuffer = null;
    private Graphics2D m_workingGraphics = null;
    private GraphicsContext m_gc = null;

    private final ExecutorService executor = TaskUtils.getVirtualExecutor();
    private final ConcurrentLinkedQueue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Effects system
    private ArrayList<ImageEffects> m_effects = new ArrayList<>();
    
    // Transform mode support
    private BufferedImage m_sourceImage = null;
    
    // Async task chaining
    private final AtomicReference<CompletableFuture<Void>> m_currentTask = 
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicBoolean m_isRealTimeTask = new AtomicBoolean(true);

    // Fit dimensions (when fitMode != NONE)
    private int m_fitWidth = 0;
    private int m_fitHeight = 0;
    
    public BufferedCanvasView() {
        super();
        m_gc = getGraphicsContext2D();
    }
    
    public BufferedCanvasView(double width, double height) {
        super(width, height);
        m_gc = getGraphicsContext2D();
    }
    
  
    public AtomicBoolean isRealTimeTask(){
        return m_isRealTimeTask;
    }
    

    // ========== Abstract Methods ==========
    
    /**
     * Get the native width for generated content
     */
    protected abstract int getGeneratedWidth();
    
    /**
     * Get the native height for generated content
     */
    protected abstract int getGeneratedHeight();
    
    /**
     * Draw content directly on the Graphics2D context
     * Override this for GENERATE or HYBRID mode
     */
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Default: no-op
    }

    // ========== Render Pipeline ==========
    
    public void updateImage() {
        
        Runnable newTask = () -> {
            try {
                BufferedImage baseImage = generateBaseImage();
                if (baseImage == null) {
                    TaskUtils.noDelay(_ -> clearCanvas());
                    return;
                }

                for (ImageEffects effect : new ArrayList<>(m_effects)) {
                    effect.applyEffect(baseImage);
                }

                BufferedImage finalImage = applyScaling(baseImage);
                TaskUtils.fxDelay(_ -> drawToCanvas(finalImage));

            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        if (m_isRealTimeTask.get()) {
            // Cancel any pending (not running) tasks
            taskQueue.clear();
        }

        taskQueue.add(newTask);
        scheduleNext();
    }

    private void scheduleNext() {
        if (isRunning.compareAndSet(false, true)) {
            Runnable next = taskQueue.poll();
            if (next == null) {
                isRunning.set(false);
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    next.run();
                } finally {
                    isRunning.set(false);
                    scheduleNext(); // run next queued task if any
                }
            }, executor);
        }
    }
    /**
     * Generate the base image based on render mode
     */
    private BufferedImage generateBaseImage() {
        switch (m_renderMode) {
            case TRANSFORM:
                return m_sourceImage;
                
            case GENERATE:
            case HYBRID:
                int width = getGeneratedWidth();
                int height = getGeneratedHeight();
                
                if (width <= 0 || height <= 0) {
                    return null;
                }
                
                // Reuse or create working buffer
                if (m_workingBuffer == null || 
                    m_workingBuffer.getWidth() != width || 
                    m_workingBuffer.getHeight() != height) {
                    
                    if (m_workingGraphics != null) {
                        m_workingGraphics.dispose();
                    }
                    
                    m_workingBuffer = new BufferedImage(
                        width, height, BufferedImage.TYPE_INT_ARGB);
                    m_workingGraphics = m_workingBuffer.createGraphics();
                    setupGraphics(m_workingGraphics);
                }
                
                // Clear buffer
                m_workingGraphics.setComposite(AlphaComposite.Clear);
                m_workingGraphics.fillRect(0, 0, width, height);
                m_workingGraphics.setComposite(AlphaComposite.SrcOver);
                
                // Draw content
                drawContent(m_workingGraphics, width, height);
                
                return m_workingBuffer;
                
            default:
                return null;
        }
    }
    
    /**
     * Apply scaling based on fit mode
     */
    private BufferedImage applyScaling(BufferedImage source) {
        if (m_fitMode == FitMode.NONE || source == null) {
            return source;
        }
        
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth = sourceWidth;
        int targetHeight = sourceHeight;
        
        switch (m_fitMode) {
            case FIT_WIDTH:
                if (m_fitWidth > 0) {
                    targetWidth = m_fitWidth;
                    // Maintain aspect ratio
                    targetHeight = (int) ((double) sourceHeight * m_fitWidth / sourceWidth);
                }
                break;
                
            case FIT_HEIGHT:
                if (m_fitHeight > 0) {
                    targetHeight = m_fitHeight;
                    // Maintain aspect ratio
                    targetWidth = (int) ((double) sourceWidth * m_fitHeight / sourceHeight);
                }
                break;
                
            case FIT_SIZE:
                if (m_fitWidth > 0 && m_fitHeight > 0) {
                    targetWidth = m_fitWidth;
                    targetHeight = m_fitHeight;
                }
                break;
            case NONE:
            default:
                
        }
        
        // Only scale if dimensions changed
        if (targetWidth != sourceWidth || targetHeight != sourceHeight) {
            return ScalingUtils.scaleImage(source, targetWidth, targetHeight, m_scalingAlgorithm);
        }
        
        return source;
    }
    
    /**
     * Draw the final image to the canvas
     */
    private void drawToCanvas(BufferedImage image) {
        if (image == null) {
            clearCanvas();
            return;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Resize canvas if needed
        if (getWidth() != width || getHeight() != height) {
            setWidth(width);
            setHeight(height);
        }
        
        // Direct pixel transfer
        PixelWriter pw = m_gc.getPixelWriter();
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        pw.setPixels(0, 0, width, height, 
            PixelFormat.getIntArgbInstance(), pixels, 0, width);
    }
    
    /**
     * Clear the canvas
     */
    private void clearCanvas() {
        m_gc.clearRect(0, 0, getWidth(), getHeight());
    }
    
    /**
     * Setup graphics rendering hints
     */
    protected void setupGraphics(Graphics2D g2d) {
        g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
    }
    
    // ========== Public API ==========
    
    public void requestRender() {
        updateImage();
    }
    
    // ========== Mode Configuration ==========
    
    public void setRenderMode(RenderMode mode) {
        m_renderMode = mode;
    }
    
    public RenderMode getRenderMode() {
        return m_renderMode;
    }
    
    public void setFitMode(FitMode mode) {
        m_fitMode = mode;
        updateImage();
    }
    
    public FitMode getFitMode() {
        return m_fitMode;
    }
    
    public void setFitWidth(int width) {
        m_fitWidth = width;
        if (m_fitMode == FitMode.FIT_WIDTH || m_fitMode == FitMode.FIT_SIZE) {
            updateImage();
        }
    }
    
    public void setFitHeight(int height) {
        m_fitHeight = height;
        if (m_fitMode == FitMode.FIT_HEIGHT || m_fitMode == FitMode.FIT_SIZE) {
            updateImage();
        }
    }
    
    public void setFitSize(int width, int height) {
        m_fitWidth = width;
        m_fitHeight = height;
        if (m_fitMode != FitMode.NONE) {
            updateImage();
        }
    }
    
    public void setScalingAlgorithm(ScalingAlgorithm algorithm) {
        m_scalingAlgorithm = algorithm;
        if (m_fitMode != FitMode.NONE) {
            updateImage();
        }
    }
    
    // ========== Transform Mode Support ==========
    
    public void setSourceImage(BufferedImage image) {
        m_sourceImage = image;
        m_renderMode = RenderMode.TRANSFORM;
        updateImage();
    }
    
    // ========== Effects System ==========
    
    public ImageEffects getEffect(NoteBytes id) {
        if (id != null && m_effects.size() > 0) {
            for (ImageEffects effect : m_effects) {
                if (effect.getId().equals(id)) {
                    return effect;
                }
            }
        }
        return null;
    }
    
    public ImageEffects getFirstNameEffect(String name) {
        if (m_effects.isEmpty()) {
            return null;
        }
        for (ImageEffects effect : m_effects) {
            if (effect.getName().equals(name)) {
                return effect;
            }
        }
        return null;
    }
    
    public void removeEffect(NoteBytes id) {
        Iterator<ImageEffects> iterator = m_effects.iterator();
        while (iterator.hasNext()) {
            ImageEffects effect = iterator.next();
            if (effect.getId().equals(id)) {
                iterator.remove();
                updateImage();
                break;
            }
        }
    }
    
    public void addEffect(ImageEffects effect) {
        m_effects.add(effect);
    }
    
    public void applyEffect(ImageEffects effect) {
        m_effects.add(effect);
        updateImage();
    }
    
    public void addEffect(int index, ImageEffects effect, boolean update) {
        m_effects.add(index, effect);
        if (update) {
            updateImage();
        }
    }
    
    public void clearEffects() {
        m_effects.clear();
        updateImage();
    }
    
    // ========== Cleanup ==========
    
    public void shutdown() {
        m_currentTask.get().cancel(true);
        if (m_workingGraphics != null) {
            m_workingGraphics.dispose();
            m_workingGraphics = null;
        }
        m_workingBuffer = null;
        m_sourceImage = null;
    }
}