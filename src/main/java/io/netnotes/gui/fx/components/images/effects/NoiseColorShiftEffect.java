package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;
import java.util.Random;

import io.netnotes.gui.fx.display.ImageHelpers;

public class NoiseColorShiftEffect extends ImageEffects {
    public static final String NAME = "NOISE_COLOR_SHIFT";
    private final float m_strength;

    public NoiseColorShiftEffect(float strength) {
        super(NAME);
        this.m_strength = strength;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        noiseColorShiftRGB(img, m_strength);
    }

    public static void noiseColorShiftRGB(BufferedImage img, float strength){
        Random rand = new Random();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                double noise = rand.nextDouble() - 0.5;
                int rgb = img.getRGB(x, y);
                img.setRGB(x, y, ImageHelpers.applyNoiseColorShift(rgb, noise, strength));
            }
        }
    }
}