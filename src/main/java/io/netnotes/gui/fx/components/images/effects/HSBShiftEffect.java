package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.gui.fx.display.ImageHelpers;

public class HSBShiftEffect extends ImageEffects {
    public static final String NAME = "HSB_SHIFT";
    private final float m_hueShift, m_satShift, m_briShift;

    public HSBShiftEffect(float hueShift, float satShift, float briShift) {
        super(NAME);
        this.m_hueShift = hueShift;
        this.m_satShift = satShift;
        this.m_briShift = briShift;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        shiftHSB(img, m_hueShift, m_satShift, m_briShift);
    }

    public static void shiftHSB(BufferedImage img, float hueShift, float satShift, float briShift){
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                img.setRGB(x, y, ImageHelpers.shiftHSB(rgb, hueShift, satShift, briShift));
            }
        }
    }
}