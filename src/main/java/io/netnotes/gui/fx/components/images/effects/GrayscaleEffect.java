package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;

public class GrayscaleEffect extends ImageEffects {
    public static final String NAME = "GRAYSCALE";
    private final double m_amount;

    public GrayscaleEffect(double amount) {
        super(NAME);
        this.m_amount = clampAmount(amount);
    }

    public GrayscaleEffect(NoteBytes id, double amount) {
        super(id, NAME);
        this.m_amount = clampAmount(amount);
    }

    @Override
    public void applyEffect(BufferedImage img) {
        grayScaleRGB(img, m_amount);
    }

   

    public static double clampAmount(double amount){
        return Math.max(0, Math.min(1, amount));
    }

     public static void grayScaleRGB(BufferedImage img, double amount){
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = (rgba >> 16) & 0xff;
                int g = (rgba >> 8) & 0xff;
                int b = rgba & 0xff;

                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                r = (int)(r * (1 - amount) + gray * amount);
                g = (int)(g * (1 - amount) + gray * amount);
                b = (int)(b * (1 - amount) + gray * amount);

                int p = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, p);
            }
        }
    }
}