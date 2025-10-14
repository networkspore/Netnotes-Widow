package io.netnotes.gui.fx.display.scaling;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

import io.netnotes.engine.utils.MathHelpers;
import io.netnotes.gui.fx.display.ImageHelpers;

public class BilinearScaling {

     /**
     * Scales the input image to the target size using bilinear interpolation.
     *
     * @param src          Source image
     * @param targetWidth  Desired width
     * @param targetHeight Desired height
     * @return Scaled image (new BufferedImage)
     */
    public static BufferedImage scaleBilinear(BufferedImage src, int targetWidth, int targetHeight) {
        // Create a compatible output image (preserve alpha if present)
        int type = (src.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, type);

        // Draw using bilinear interpolation
        Graphics2D g2 = scaled.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2.dispose();
        }

        return scaled;
    }

    /**
     * Bilinear interpolation scaling - good quality/performance balance
     */
    public static BufferedImage scaleBilinearRGB(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        BigDecimal xRatio = MathHelpers.divideNearestNeighbor(srcWidth - 1, targetWidth);
        BigDecimal yRatio = MathHelpers.divideNearestNeighbor(srcHeight - 1, targetHeight);
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                BigDecimal srcX = MathHelpers.multiplyLong(xRatio, x);
                BigDecimal srcY = MathHelpers.multiplyLong(yRatio, y);
                
                int x1 = srcX.intValue();
                int y1 = srcY.intValue();
                int x2 = Math.min(x1 + 1, srcWidth - 1);
                int y2 = Math.min(y1 + 1, srcHeight - 1);
                
                BigDecimal xWeight = srcX.subtract(BigDecimal.valueOf(x1));
                BigDecimal yWeight = srcY.subtract(BigDecimal.valueOf(y1));
                
                int rgb = BilinearScaling.bilinearInterpolate(
                    src.getRGB(x1, y1), src.getRGB(x2, y1),
                    src.getRGB(x1, y2), src.getRGB(x2, y2),
                    xWeight, yWeight
                );
                
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }


    public static BufferedImage scaleBilinearCrop(BufferedImage src, int cropX1, int cropY1, int cropX2, int cropY2, int targetWidth, int targetHeight) {
        // Clamp crop bounds to image dimensions
        cropX1 = Math.max(0, cropX1);
        cropY1 = Math.max(0, cropY1);
        cropX2 = Math.min(src.getWidth(), cropX2);
        cropY2 = Math.min(src.getHeight(), cropY2);
        
        int cropWidth = cropX2 - cropX1;
        int cropHeight = cropY2 - cropY1;
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop bounds: width and height must be > 0.");
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        // Use BigDecimal for ratio calculations
        BigDecimal xRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(cropWidth - 1), 
            BigDecimal.valueOf(targetWidth)
        );
        BigDecimal yRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(cropHeight - 1), 
            BigDecimal.valueOf(targetHeight)
        );
        
        for (int y = 0; y < targetHeight; y++) {
            // Calculate source Y position using BigDecimal
            BigDecimal srcYDecimal = BigDecimal.valueOf(cropY1).add(
                BigDecimal.valueOf(y).multiply(yRatio)
            );
            int y1 = srcYDecimal.intValue();
            int y2 = Math.min(y1 + 1, cropY2 - 1);
            BigDecimal yWeight = srcYDecimal.subtract(BigDecimal.valueOf(y1));
            
            for (int x = 0; x < targetWidth; x++) {
                // Calculate source X position using BigDecimal
                BigDecimal srcXDecimal = BigDecimal.valueOf(cropX1).add(
                    BigDecimal.valueOf(x).multiply(xRatio)
                );
                int x1 = srcXDecimal.intValue();
                int x2 = Math.min(x1 + 1, cropX2 - 1);
                BigDecimal xWeight = srcXDecimal.subtract(BigDecimal.valueOf(x1));
                
                int rgb = BilinearScaling.bilinearInterpolate(
                    src.getRGB(x1, y1), src.getRGB(x2, y1),
                    src.getRGB(x1, y2), src.getRGB(x2, y2),
                    xWeight, yWeight
                );
                result.setRGB(x, y, rgb);
            }
        }
        return result;
    }

    public static int bilinearInterpolate(int rgb00, int rgb10, int rgb01, int rgb11, 
        BigDecimal xWeight, BigDecimal yWeight
    ) {
        // Extract color components
        int a00 = (rgb00 >> 24) & 0xFF;
        int r00 = (rgb00 >> 16) & 0xFF;
        int g00 = (rgb00 >> 8) & 0xFF;
        int b00 = rgb00 & 0xFF;
        
        int a10 = (rgb10 >> 24) & 0xFF;
        int r10 = (rgb10 >> 16) & 0xFF;
        int g10 = (rgb10 >> 8) & 0xFF;
        int b10 = rgb10 & 0xFF;
        
        int a01 = (rgb01 >> 24) & 0xFF;
        int r01 = (rgb01 >> 16) & 0xFF;
        int g01 = (rgb01 >> 8) & 0xFF;
        int b01 = rgb01 & 0xFF;
        
        int a11 = (rgb11 >> 24) & 0xFF;
        int r11 = (rgb11 >> 16) & 0xFF;
        int g11 = (rgb11 >> 8) & 0xFF;
        int b11 = rgb11 & 0xFF;
        
        // Pre-calculate weight terms for efficiency
        BigDecimal oneMinusX = BigDecimal.ONE.subtract(xWeight);
        BigDecimal oneMinusY = BigDecimal.ONE.subtract(yWeight);
        
        BigDecimal w00 = oneMinusX.multiply(oneMinusY);
        BigDecimal w10 = xWeight.multiply(oneMinusY);
        BigDecimal w01 = oneMinusX.multiply(yWeight);
        BigDecimal w11 = xWeight.multiply(yWeight);
        
        // Interpolate each channel
        int a = ImageHelpers.interpolateChannel(a00, a10, a01, a11, w00, w10, w01, w11);
        int r = ImageHelpers.interpolateChannel(r00, r10, r01, r11, w00, w10, w01, w11);
        int g = ImageHelpers.interpolateChannel(g00, g10, g01, g11, w00, w10, w01, w11);
        int b = ImageHelpers.interpolateChannel(b00, b10, b01, b11, w00, w10, w01, w11);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
}
