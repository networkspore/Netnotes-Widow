package io.netnotes.gui.fx.components.images.scaling;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

import io.netnotes.engine.utils.MathHelpers;

public class BicubicScaling {

    /**
     * Bicubic scaling using Graphics2D - high quality
     */
    public static BufferedImage scaleBicubic(BufferedImage src, int targetWidth, int targetHeight) {
        int type = (src.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D g2d = result.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        
        return result;
    }

    public static BufferedImage scaleBicubicCrop(BufferedImage src, int cropX1, int cropY1, int cropX2, int cropY2,
                                            int targetWidth, int targetHeight) {
        // Clamp crop bounds to image dimensions
        cropX1 = Math.max(0, cropX1);
        cropY1 = Math.max(0, cropY1);
        cropX2 = Math.min(src.getWidth(), cropX2);
        cropY2 = Math.min(src.getHeight(), cropY2);
        
        int cropWidth = cropX2 - cropX1;
        int cropHeight = cropY2 - cropY1;
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop bounds");
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Calculate scaling ratios using BigDecimal for precision
        BigDecimal scaleX = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(targetWidth),
            BigDecimal.valueOf(cropWidth)
        );
        BigDecimal scaleY = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(targetHeight),
            BigDecimal.valueOf(cropHeight)
        );
        
        // Calculate the position offset for the crop area in the full image
        BigDecimal cropCenterX = BigDecimal.valueOf(cropX1 + cropX2).divide(BigDecimal.valueOf(2));
        BigDecimal cropCenterY = BigDecimal.valueOf(cropY1 + cropY2).divide(BigDecimal.valueOf(2));
        
        BigDecimal imageCenterX = BigDecimal.valueOf(src.getWidth()).divide(BigDecimal.valueOf(2));
        BigDecimal imageCenterY = BigDecimal.valueOf(src.getHeight()).divide(BigDecimal.valueOf(2));
        
        // Calculate offset from image center to crop center
        BigDecimal offsetX = cropCenterX.subtract(imageCenterX);
        BigDecimal offsetY = cropCenterY.subtract(imageCenterY);
        
        // Scale the entire source image
        int scaledWidth = MathHelpers.multiplyLong(scaleX, src.getWidth()).setScale(0, RoundingMode.CEILING).intValue();
        int scaledHeight = MathHelpers.multiplyLong(scaleY, src.getHeight()).setScale(0, RoundingMode.CEILING).intValue();
        
        // Calculate scaled offset
        int scaledOffsetX = MathHelpers.multiplyToInt(scaleX.multiply(offsetX), 1);
        int scaledOffsetY = MathHelpers.multiplyToInt(scaleY.multiply(offsetY), 1);
        
        // Calculate destination position (centered, with offset)
        int destX = MathHelpers.divideNearestNeighborToInt((targetWidth - scaledWidth), 2) + scaledOffsetX;
        int destY =  MathHelpers.divideNearestNeighborToInt(targetHeight - scaledHeight, 2) + scaledOffsetY;
        
        // Draw the entire source image at the calculated position
        g2d.drawImage(
            src,
            destX, destY, destX + scaledWidth, destY + scaledHeight, // destination rect
            0, 0, src.getWidth(), src.getHeight(), // entire source image
            null
        );
        
        g2d.dispose();
        return result;
    }
    
}
