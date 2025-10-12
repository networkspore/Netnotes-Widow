package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.gui.fx.display.ImageHelpers;

public class RotateEffect extends ImageEffects {
    public static final String NAME = "ROTATE";
    private final double m_angle;

    public RotateEffect(double angleRadians) {
        super(NAME);
        this.m_angle = angleRadians;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        rotateRGB(img, m_angle);
    }

    public static void rotateRGB(BufferedImage img, double angle){
        BufferedImage rotated = ImageHelpers.rotateImage(img, angle);
        img.getGraphics().drawImage(rotated, 0, 0, null);
    }
}
