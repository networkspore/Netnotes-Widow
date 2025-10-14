package io.netnotes.gui.fx.components.images;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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
    private Image m_defaultImg = null;
    private ArrayList<ImageEffects> m_effects = new ArrayList<ImageEffects>();
    private final AtomicReference<CompletableFuture<Void>> m_currentTask = new AtomicReference<>(CompletableFuture.completedFuture(null));



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

    public void setDefaultImage(Image image) {
        m_defaultImg = image;
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
        if (m_defaultImg == null) {
            super.setImage(null);
        }else if (!m_effects.isEmpty()) {
            // Capture current state
            final Image sourceImage = m_defaultImg;
            final ArrayList<ImageEffects> effectsSnapshot = new ArrayList<>(m_effects);

            // Chain this task after the previous one
            m_currentTask.updateAndGet(previousTask -> 
                previousTask.thenRunAsync(() -> {
                    try {
                        // Process in background (virtual thread)
                        BufferedImage workingImage = SwingFXUtils.fromFXImage(sourceImage, null);
                        for (ImageEffects effect : effectsSnapshot) {
                            effect.applyEffect(workingImage);
                        }
                        Image resultImage = SwingFXUtils.toFXImage(workingImage, null);
                        TaskUtils.fxDelay(30, (onSucceeded)->super.setImage(resultImage));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, TaskUtils.getVirtualExecutor())
            );
        }else{
            super.setImage(m_defaultImg);
        }
    }
   
    
}