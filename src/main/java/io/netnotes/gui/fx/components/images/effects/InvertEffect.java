package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;

public class InvertEffect extends ImageEffects {


    public static String NAME = "INVERT";

    private double m_amount = 1.0;

    public InvertEffect(double amount) {
        super(NAME);
        m_amount = amount;
    }

    public InvertEffect(NoteBytes id, double amount) {
        super(id, NAME);
        m_amount =  clampAmount(amount); 
        
    }

    @Override
    public void applyEffect(BufferedImage img) {
        invertRGB(img, m_amount);
    }

    private static double clampAmount(double amount){
        return Math.min(1, Math.max(-1, amount));
    }


    public static void invertRGB(BufferedImage img, double amount) {

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgba = img.getRGB(x, y);

                int a = (rgba >> 24) & 0xff;
                int r = (rgba >> 16) & 0xff;
                int g = (rgba >> 8) & 0xff;
                int b = rgba & 0xff;

                int inv = (int) (0xff * amount);

                r = Math.abs(inv - r);
                g = Math.abs(inv - g);
                b = Math.abs(inv - b);

                int p = (a << 24) | (r << 16) | (g << 8) | b;

                img.setRGB(x, y, p);
            }
        }
    }
}
