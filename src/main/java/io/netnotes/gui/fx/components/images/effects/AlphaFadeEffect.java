package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;

public class AlphaFadeEffect extends ImageEffects {
    public static final String NAME = "ALPHA_FADE";
    private final double m_amount; // 0 = fully transparent, 1 = no change

    public AlphaFadeEffect(double amount) {
        super(NAME);
        this.m_amount = clampAmount(amount);
    }

    public AlphaFadeEffect(NoteBytes id, double amount) {
        super(id, NAME);
        this.m_amount = clampAmount(amount);
    }

    public static double clampAmount(double amount){
        return  Math.max(0, Math.min(1, amount));
    }

    @Override
    public void applyEffect(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = (rgba >> 16) & 0xff;
                int g = (rgba >> 8) & 0xff;
                int b = rgba & 0xff;

                a = (int)(a * m_amount);

                int p = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, p);
            }
        }
    }
}