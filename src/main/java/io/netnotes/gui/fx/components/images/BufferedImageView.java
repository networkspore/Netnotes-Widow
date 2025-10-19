package io.netnotes.gui.fx.components.images;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.components.images.effects.BrightnessEffect;
import io.netnotes.gui.fx.components.images.effects.ImageEffects;
import io.netnotes.gui.fx.components.images.effects.InvertEffect;
import io.netnotes.gui.fx.utils.TaskUtils;

public class BufferedImageView extends ImageView {
    // Rendering modes
    public enum RenderMode {
        TRANSFORM,  // Apply effects to existing image
        GENERATE,   // Generate image from scratch
        HYBRID      // Generate base, then apply effects
    }

    private RenderMode m_renderMode = RenderMode.TRANSFORM;
    private Image m_defaultImg = null;
    private ArrayList<ImageEffects> m_effects = new ArrayList<>();
    private final AtomicReference<CompletableFuture<Void>> m_currentTask = 
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    

    public BufferedImageView() {
        super();
        setPreserveRatio(true);
    } 
    
    public BufferedImageView(double fitWidth){
        this();
        setPreserveRatio(true);
        setFitWidth(fitWidth);
    }

    public BufferedImageView(Image image, double imageWidth) {
        super();
       
        setDefaultImage(image, imageWidth);



    }



    public BufferedImageView(Image image, boolean fitWidth) {
        super();
        setDefaultImage(image);

        setPreserveRatio(true);
        if (fitWidth) {
            setFitWidth(image.getWidth());
        }

    }

    public BufferedImageView(Image image) {
        super();
        setDefaultImage(image);
    }

     // Rendering pipeline hooks
    
    /**
     * Override this to generate the base image from scratch
     * Called when renderMode is GENERATE or HYBRID
     */
    protected BufferedImage generateBaseImage(int width, int height) {
        return null; // Default: no generation
    }
    
    /**
     * Override this to draw content on the image
     * Alternative to generateBaseImage for direct drawing
     */
    protected void drawContent(Graphics2D g2d, int width, int height) {
        // Default: no-op
    }
    
    /**
     * Get the dimensions for generated images
     */
    protected int getGeneratedWidth(){
        throw new IllegalStateException("getGeneratedWidth is not implemented");
    }

    protected int getGeneratedHeight(){
         throw new IllegalStateException("getGeneratedHeight is not implemented");
    }
    

    public void setDefaultImage(Image image){
        setDefaultImage(image, m_renderMode);
    }

    public void setDefaultImage(Image image, RenderMode mode) {
        m_defaultImg = image;
        updateImage();
    }
    
    // New method for GENERATE mode
    public void setRenderMode(RenderMode mode) {
        m_renderMode = mode;
        updateImage();
    }


    public void setDefaultImage(Image img, double fitWidth) {
        setDefaultImage(img);
        setFitWidth(fitWidth);
        setPreserveRatio(true);
    }

    public ImageEffects getEffect(NoteBytes id) {
        if (id != null && m_effects.size() > 0) {
            for (int i = 0; i < m_effects.size(); i++) {
                ImageEffects effect = m_effects.get(i);
                if (effect.getId().equals(id)) {
                    return effect;
                }
            }
        }
        return null;
    }

    public ImageEffects getFirstNameEffect(String name) {
        if (m_effects.size() == 0) {
            return null;
        }
        for (int i = 0; i < m_effects.size(); i++) {
            ImageEffects effect = m_effects.get(i);
            if (effect.getName().equals(name)) {
                return effect;
            }

        }
        return null;
    }

    public void applyInvertEffect(double amount) {
        m_effects.add(new InvertEffect(amount));
        updateImage();
    }

    public void applyInvertEffect(NoteBytes id, double amount) {
        if (getEffect(id) == null) {
            m_effects.add(new InvertEffect(id, amount));
            updateImage();
        }
    }

    public void applyBrightnessEffect(NoteBytes id, double amount) {
        if (getEffect(id) == null) {
            m_effects.add(new BrightnessEffect(id, amount));
            updateImage();
        }
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


    

     public void updateImage() {
        // Chain this task after the previous one
        m_currentTask.updateAndGet(previousTask -> 
            previousTask.thenRunAsync(() -> {
                try {
                    BufferedImage workingImage = null;
                    
                    // Phase 1: Get base image based on mode
                    switch (m_renderMode) {
                        case TRANSFORM:
                            if (m_defaultImg == null) {
                                TaskUtils.fxDelay(_->super.setImage(null));
                                return;
                            }
                            workingImage = SwingFXUtils.fromFXImage(m_defaultImg, null);
                            break;
                            
                        case GENERATE:
                            int width = getGeneratedWidth();
                            int height = getGeneratedHeight();
                            
                            // Try generateBaseImage first
                            workingImage = generateBaseImage(width, height);
                            
                            // Fall back to drawContent if generateBaseImage returns null
                            if (workingImage == null) {
                                workingImage = new BufferedImage(
                                    width, height, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g2d = workingImage.createGraphics();
                                setupGraphics(g2d);
                                drawContent(g2d, width, height);
                                g2d.dispose();
                            }
                            break;
                            
                        case HYBRID:
                            // Generate base
                            width = getGeneratedWidth();
                            height = getGeneratedHeight();
                            workingImage = generateBaseImage(width, height);
                            
                            if (workingImage == null) {
                                workingImage = new BufferedImage(
                                    width, height, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g2d = workingImage.createGraphics();
                                setupGraphics(g2d);
                                drawContent(g2d, width, height);
                                g2d.dispose();
                            }
                            break;
                    }
                    
                    // Phase 2: Apply effects (works for all modes)
                    if (!m_effects.isEmpty()) {
                        for (ImageEffects effect : new ArrayList<>(m_effects)) {
                            effect.applyEffect(workingImage);
                        }
                    }
                    
                    // Phase 3: Convert and display
                    Image resultImage = SwingFXUtils.toFXImage(workingImage, null);
                    TaskUtils.fxDelay(_->super.setImage(resultImage));
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, TaskUtils.getVirtualExecutor())
        );
    }
   
    /**
     * Helper to setup graphics rendering hints
     */
    protected void setupGraphics(Graphics2D g2d) {
        g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    }
    
    public void shutdown(){

    }
}