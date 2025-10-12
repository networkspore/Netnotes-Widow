package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;

public class BrightnessEffect extends ImageEffects {
    public static final String NAME = "BRIGHTNESS";
    private final double m_amount; // range: -1.0 (darken) to 1.0 (brighten)

    public BrightnessEffect(double amount) {
        super(NAME);
        this.m_amount = clampAmount(amount);
    }

    public BrightnessEffect(NoteBytes id, double amount) {
        super(id, NAME);
        this.m_amount = clampAmount(amount);
    }

    @Override
    public void applyEffect(BufferedImage img) {
        brightnessRGB(img, m_amount);
    }

    public static void brightnessRGB(BufferedImage img, double amount){
        int delta = (int)(255 * amount);
 
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = clamp((rgba >> 16) & 0xff + delta);
                int g = clamp((rgba >> 8) & 0xff + delta);
                int b = clamp(rgba & 0xff + delta);

                int p = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, p);
            }
        }
    }

    private static double clampAmount(double amount){
        return Math.min(1, Math.max(-1, amount));
    }


    private static int clamp(int val) {
        return Math.min(255, Math.max(0, val));
    }
}
