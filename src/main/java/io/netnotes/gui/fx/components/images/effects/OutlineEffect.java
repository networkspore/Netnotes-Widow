package io.netnotes.gui.fx.components.images.effects;

import java.awt.Color;
import java.awt.image.BufferedImage;
import io.netnotes.engine.noteBytes.NoteBytes;


public class OutlineEffect extends ImageEffects {
    public static final String NAME = "OUTLINE";

    private final Color m_color;
    private final int m_radius; // in pixels
    private final int m_alpha;  // 0–255

    public OutlineEffect(Color color, int radius, int alpha) {
        super(NAME);
        m_color = color;
        m_radius = Math.max(1, radius);
        m_alpha = Math.max(0, Math.min(255, alpha));
    }

    public OutlineEffect(NoteBytes id, Color color, int radius, int alpha) {
        super(id, NAME);
        m_color = color;
        m_radius = Math.max(1, radius);
        m_alpha = Math.max(0, Math.min(255, alpha));
    }

    @Override
    public void applyEffect(BufferedImage img) {
        outlineRGB(img, m_color, m_radius, m_alpha);
    }

    public static void outlineRGB(BufferedImage img, Color color, int radius, int alpha){
         int width = img.getWidth();
        int height = img.getHeight();

        // Store pixels where the outline should be applied
        boolean[][] outlineMask = new boolean[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xff;

                if (a > 0) {
                    // For each neighboring pixel in radius
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            int nx = x + dx;
                            int ny = y + dy;

                            if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                                int neighborAlpha = (img.getRGB(nx, ny) >> 24) & 0xff;
                                if (neighborAlpha == 0) {
                                    outlineMask[nx][ny] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply the outline color to marked pixels
        int outlineARGB = (alpha << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (outlineMask[x][y]) {
                    img.setRGB(x, y, outlineARGB);
                }
            }
        }
    }
}