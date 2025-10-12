package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;
import java.util.Random;

import io.netnotes.gui.fx.display.ImageHelpers;

public class MicroDisplacementEffect extends ImageEffects {
    public static final String NAME = "MICRO_DISPLACE";
    private final int m_displacement;

    public MicroDisplacementEffect(int maxDisplacement) {
        super(NAME);
        this.m_displacement = maxDisplacement;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        microDisplacementRGB(img, m_displacement);
    }

    public void microDisplacementRGB(BufferedImage img, int displacement){
        BufferedImage displaced = ImageHelpers.applyMicroDisplacement(img, new Random(), m_displacement);
        img.getGraphics().drawImage(displaced, 0, 0, null);
    }
}
