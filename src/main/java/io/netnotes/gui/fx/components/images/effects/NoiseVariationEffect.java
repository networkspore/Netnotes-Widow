package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;
import java.util.Random;

import io.netnotes.gui.fx.display.ImageHelpers;

public class NoiseVariationEffect extends ImageEffects {
    public static final String NAME = "NOISE_VARIATION";
    private final float m_strength;

    public NoiseVariationEffect(float strength) {
        super(NAME);
        this.m_strength = strength;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        noiseColorShiftRGB(img, m_strength);
    }

    public static void noiseColorShiftRGB(BufferedImage img, float strength){
        BufferedImage noisy = ImageHelpers.applyNoiseVariation(img, new Random(), strength);
        img.getGraphics().drawImage(noisy, 0, 0, null);
    }
}
