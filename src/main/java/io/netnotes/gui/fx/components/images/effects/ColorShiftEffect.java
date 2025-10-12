package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;
import java.util.Random;

import io.netnotes.gui.fx.display.ImageHelpers;

public class ColorShiftEffect extends ImageEffects {
    public static final String NAME = "COLOR_SHIFT";
    private final float m_strength;

    public ColorShiftEffect(float strength) {
        super(NAME);
        this.m_strength = strength;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        colorShiftRGB(img, m_strength);
    }

    public static void colorShiftRGB(BufferedImage img, float strength){
        BufferedImage shifted = ImageHelpers.applyColorShift(img, new Random(), strength);
        img.getGraphics().drawImage(shifted, 0, 0, null);
    }
}

