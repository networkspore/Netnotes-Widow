package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;
import io.netnotes.engine.noteBytes.NoteBytes;

public class BlurEffect extends ImageEffects {

    public static final String NAME = "BLUR";

    private final int m_radius;

    public BlurEffect(int radius) {
        super(NAME);
        this.m_radius = Math.max(1, radius);
    }

    public BlurEffect(NoteBytes id, int radius) {
        super(id, NAME);
        this.m_radius = Math.max(1, radius);
    }

    @Override
    public void applyEffect(BufferedImage img) {
        blurRGB(img, m_radius);   
    }

    public static void blurRGB(BufferedImage img, int radius){
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                int r = 0, g = 0, b = 0, a = 0, count = 0;

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;

                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            int argb = img.getRGB(nx, ny);
                            a += (argb >> 24) & 0xff;
                            r += (argb >> 16) & 0xff;
                            g += (argb >> 8) & 0xff;
                            b += argb & 0xff;
                            count++;
                        }
                    }
                }

                a /= count;
                r /= count;
                g /= count;
                b /= count;

                int blurredPixel = (a << 24) | (r << 16) | (g << 8) | b;
                temp.setRGB(x, y, blurredPixel);
            }
        }

        // Copy blurred image back
        img.getGraphics().drawImage(temp, 0, 0, null);
    }
}