package io.netnotes.gui.fx.components.images;


import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.components.images.effects.BrightnessEffect;
import io.netnotes.gui.fx.components.images.effects.ImageEffects;
import io.netnotes.gui.fx.components.images.effects.InvertEffect;

public class BufferedImageView extends ImageView {
    private Image m_defaultImg = null;
    private BufferedImage m_img;
    private ArrayList<ImageEffects> m_effects = new ArrayList<ImageEffects>();

    public BufferedImageView() {
        super();
        m_img = null;
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
        if(image != null){
            m_defaultImg = image;

            m_img = new BufferedImage((int) image.getWidth(),(int) image.getHeight(),  BufferedImage.TYPE_INT_ARGB);
            m_img = SwingFXUtils.fromFXImage(image, m_img);
            
            updateImage();
            setImage(SwingFXUtils.toFXImage(m_img, null));
        }else{
            m_img = null;
            m_defaultImg = null;
            setImage(null);
        }
       // setImage(m_img);
        
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

        if (m_img != null && m_defaultImg != null) {
        
         
            m_img = SwingFXUtils.fromFXImage(m_defaultImg, m_img);
           
            if (!m_effects.isEmpty()) {
           

                for (int i = 0; i < m_effects.size(); i++) {
                    ImageEffects effect = m_effects.get(i);
                    effect.applyEffect(m_img);
                }

            }
        } 
        setImage(SwingFXUtils.toFXImage(m_img, null));
    }

    public BufferedImage getBaseImage() {
        return m_img;
    }

    public void setBufferedImage(BufferedImage imgBuf) {
        m_img = imgBuf;
        if (m_img != null) {
            if (m_effects.size() > 0) {

                for (int i = 0; i < m_effects.size(); i++) {
                    ImageEffects effect = m_effects.get(i);
                    effect.applyEffect(imgBuf);
                }


            }

            setImage(SwingFXUtils.toFXImage(imgBuf, null));
        } else {
            setImage(null);
        }
    }
}