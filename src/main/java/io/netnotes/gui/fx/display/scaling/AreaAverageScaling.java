package io.netnotes.gui.fx.display.scaling;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;

import io.netnotes.engine.utils.MathHelpers;

public class AreaAverageScaling {

    /**
     * Area averaging for downscaling - reduces aliasing
     */
    public static BufferedImage scaleAreaAveraging(BufferedImage src, int targetWidth, int targetHeight) {
        // For upscaling, fall back to bilinear
        if (targetWidth > src.getWidth() || targetHeight > src.getHeight()) {
            return BilinearScaling.scaleBilinear(src, targetWidth, targetHeight);
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        BigDecimal xRatio = MathHelpers.divideNearestNeighbor(src.getWidth(), targetWidth);
        BigDecimal yRatio =  MathHelpers.divideNearestNeighbor(src.getHeight(), targetHeight);
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int srcX1 = MathHelpers.multiplyToInt(xRatio, x);
                int srcY1 = MathHelpers.multiplyToInt(yRatio, y);
                int srcX2 = MathHelpers.multiplyToInt(xRatio, x + 1);
                int srcY2 = MathHelpers.multiplyToInt(yRatio, (y + 1));
                
                srcX2 = Math.min(srcX2, src.getWidth());
                srcY2 = Math.min(srcY2, src.getHeight());
                
                long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
                int pixelCount = 0;
                
                for (int sy = srcY1; sy < srcY2; sy++) {
                    for (int sx = srcX1; sx < srcX2; sx++) {
                        int rgb = src.getRGB(sx, sy);
                        totalA += (rgb >> 24) & 0xFF;
                        totalR += (rgb >> 16) & 0xFF;
                        totalG += (rgb >> 8) & 0xFF;
                        totalB += rgb & 0xFF;
                        pixelCount++;
                    }
                }
                
                if (pixelCount > 0) {
                    int avgA = MathHelpers.divideNearestNeighborToInt(totalA, pixelCount);
                    int avgR = MathHelpers.divideNearestNeighborToInt(totalR , pixelCount);
                    int avgG = MathHelpers.divideNearestNeighborToInt(totalG , pixelCount);
                    int avgB = MathHelpers.divideNearestNeighborToInt(totalB , pixelCount);
                    
                    int avgRGB = (avgA << 24) | (avgR << 16) | (avgG << 8) | avgB;
                    result.setRGB(x, y, avgRGB);
                }
            }
        }
        
        return result;
    }

    public static BufferedImage scaleAreaAveraging(BufferedImage src, int cropX1, int cropY1, int cropX2, int cropY2, int targetWidth, int targetHeight) {
        // Clamp crop bounds
        cropX1 = Math.max(0, cropX1);
        cropY1 = Math.max(0, cropY1);
        cropX2 = Math.min(src.getWidth(), cropX2);
        cropY2 = Math.min(src.getHeight(), cropY2);
        
        int cropWidth = cropX2 - cropX1;
        int cropHeight = cropY2 - cropY1;
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop bounds: zero or negative size.");
        }
        
        // If we're upscaling, delegate to bilinear
        if (targetWidth > cropWidth || targetHeight > cropHeight) {
            return BilinearScaling.scaleBilinearCrop(src, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight);
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        // Use BigDecimal for ratio calculations
        BigDecimal xRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(cropWidth), 
            BigDecimal.valueOf(targetWidth)
        );
        BigDecimal yRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(cropHeight), 
            BigDecimal.valueOf(targetHeight)
        );
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // Calculate source bounds using BigDecimal
                int srcX1 = cropX1 + MathHelpers.multiplyToInt(xRatio, x);
                int srcY1 = cropY1 + MathHelpers.multiplyToInt(yRatio, y);
                int srcX2 = cropX1 + MathHelpers.multiplyToInt(xRatio, x + 1);
                int srcY2 = cropY1 + MathHelpers.multiplyToInt(yRatio, y + 1);
                
                srcX2 = Math.min(srcX2, cropX2);
                srcY2 = Math.min(srcY2, cropY2);
                
                long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
                int pixelCount = 0;
                
                for (int sy = srcY1; sy < srcY2; sy++) {
                    for (int sx = srcX1; sx < srcX2; sx++) {
                        int rgb = src.getRGB(sx, sy);
                        totalA += (rgb >> 24) & 0xFF;
                        totalR += (rgb >> 16) & 0xFF;
                        totalG += (rgb >> 8) & 0xFF;
                        totalB += rgb & 0xFF;
                        pixelCount++;
                    }
                }
                
                if (pixelCount > 0) {
                    int avgA = MathHelpers.divideNearestNeighborToInt(totalA, pixelCount);
                    int avgR = MathHelpers.divideNearestNeighborToInt(totalR, pixelCount);
                    int avgG = MathHelpers.divideNearestNeighborToInt(totalG, pixelCount);
                    int avgB = MathHelpers.divideNearestNeighborToInt(totalB, pixelCount);
                    int avgRGB = (avgA << 24) | (avgR << 16) | (avgG << 8) | avgB;
                    result.setRGB(x, y, avgRGB);
                }
            }
        }
        return result;
    }
    
}
