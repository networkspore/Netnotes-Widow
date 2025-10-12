package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.display.ImageHelpers;

public class ContrastEffect extends ImageEffects {
    public static final String NAME = "CONTRAST";
    private final double m_amount; // range: -1.0 to 1.0

    public ContrastEffect(double amount) {
        super(NAME);
        this.m_amount = clampAmount(amount);
    }

    public ContrastEffect(NoteBytes id, double amount) {
        super(id, NAME);
        this.m_amount = clampAmount(amount);
    }

    private static double clampAmount(double amount){
        return Math.min(1, Math.max(-1, amount));
    }


    @Override
    public void applyEffect(BufferedImage img) {
       constrastRGB(img, m_amount);
    }

    
    public static void constrastRGB(BufferedImage img, double amount){
         double factor = (259 * (amount * 255 + 255)) / (255 * (259 - amount * 255));

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = ImageHelpers.clampRGB((int)(factor * ((rgba >> 16) & 0xff - 128) + 128));
                int g = ImageHelpers.clampRGB((int)(factor * ((rgba >> 8) & 0xff - 128) + 128));
                int b = ImageHelpers.clampRGB((int)(factor * ((rgba & 0xff) - 128) + 128));

                int p = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, p);
            }
        }
    }
}