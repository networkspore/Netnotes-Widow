package io.netnotes.gui.fx.components.images.effects;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class DropShadowEffect extends ImageEffects {
    public static final String NAME = "DROP_SHADOW";

    private final int m_offsetX;
    private final int m_offsetY;
    private final int m_blurRadius;
    private final Color m_color;

    public DropShadowEffect(int offsetX, int offsetY, int blurRadius, Color color) {
        super(NAME);
        this.m_offsetX = offsetX;
        this.m_offsetY = offsetY;
        this.m_blurRadius = blurRadius;
        this.m_color = color;
    }

    @Override
    public void applyEffect(BufferedImage img) {
        dropShadowRGB(img, m_offsetX, m_offsetY, m_blurRadius, m_color);
    }

    public static void dropShadowRGB(BufferedImage img, int offsetX, int offsetY, int blurRadius, Color color){
        BufferedImage shadowLayer = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Create a black (or colored) silhouette of non-transparent pixels
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int alpha = (img.getRGB(x, y) >> 24) & 0xff;
                if (alpha > 0) {
                    int sx = x + offsetX;
                    int sy = y + offsetX;
                    if (sx >= 0 && sx < img.getWidth() && sy >= 0 && sy < img.getHeight()) {
                        int shadowARGB = (color.getAlpha() << 24) |
                                (color.getRed() << 16) |
                                (color.getGreen() << 8) |
                                color.getBlue();
                        shadowLayer.setRGB(sx, sy, shadowARGB);
                    }
                }
            }
        }

        // Blur shadow layer
        BlurEffect.blurRGB(shadowLayer, blurRadius);

        // Composite shadow and original image
        img.getGraphics().drawImage(shadowLayer, 0, 0, null);
        // Original image is already present in img
    }
}
