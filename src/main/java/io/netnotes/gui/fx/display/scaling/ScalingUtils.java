package io.netnotes.gui.fx.display.scaling;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

import io.netnotes.engine.utils.MathHelpers;
import io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm;

public class ScalingUtils {
    public static final BigDecimal halfThreshold = new BigDecimal("0.5");

    public static BufferedImage scaleProgressiveCrop(
        BufferedImage src,
        int cropX1, int cropY1, int cropX2, int cropY2,
        int targetWidth, int targetHeight,
        ScalingAlgorithm algorithm
    ) {
        // Clamp crop bounds
        cropX1 = Math.max(0, cropX1);
        cropY1 = Math.max(0, cropY1);
        cropX2 = Math.min(src.getWidth(), cropX2);
        cropY2 = Math.min(src.getHeight(), cropY2);
        
        int cropWidth = cropX2 - cropX1;
        int cropHeight = cropY2 - cropY1;
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop bounds");
        }
        
        // Check if we need progressive scaling (downscaling by more than 50%)
        BigDecimal widthRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(targetWidth),
            BigDecimal.valueOf(cropWidth)
        );
        BigDecimal heightRatio = MathHelpers.divideNearestNeighbor(
            BigDecimal.valueOf(targetHeight),
            BigDecimal.valueOf(cropHeight)
        );
        
        // If scaling down significantly, use progressive steps
        if (widthRatio.compareTo(halfThreshold) < 0 || heightRatio.compareTo(halfThreshold) < 0) {
            int currentWidth = cropWidth;
            int currentHeight = cropHeight;
            int currentX1 = cropX1;
            int currentY1 = cropY1;
            int currentX2 = cropX2;
            int currentY2 = cropY2;
            BufferedImage current = src;
            
            
            // Progressive downscaling - halve dimensions until close to target
            while (currentWidth > targetWidth * 2 || currentHeight > targetHeight * 2) {
                int newWidth = Math.max(targetWidth, MathHelpers.divideNearestNeighborToInt(currentWidth, 2));
                int newHeight = Math.max(targetHeight, MathHelpers.divideNearestNeighborToInt(currentHeight, 2));
                
                current = ScalingUtils.scaleImage(current, currentX1, currentY1, currentX2, currentY2, 
                                newWidth, newHeight, algorithm);
                
                // Update dimensions and bounds for next iteration
                currentWidth = newWidth;
                currentHeight = newHeight;
                currentX1 = 0;
                currentY1 = 0;
                currentX2 = newWidth;
                currentY2 = newHeight;
            }
            
            // Final pass to exact target size
            return ScalingUtils.scaleImage(current, 0, 0, currentWidth, currentHeight, targetWidth, targetHeight, algorithm);
        } else {
            // Small scale or upscaling — single pass
            return ScalingUtils.scaleImage(src, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight, algorithm);
        }
    }

    /**
     * Progressive scaling for large size differences - better quality
     */
    public static BufferedImage scaleProgressive(BufferedImage src, int targetWidth, int targetHeight, 
                                            io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm algorithm) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // If scaling down by more than 50%, do it progressively
        if (targetWidth < srcWidth * 0.5 || targetHeight < srcHeight * 0.5) {
            BufferedImage current = src;
            
            while (current.getWidth() > targetWidth * 2 || current.getHeight() > targetHeight * 2) {
                int newWidth = Math.max(current.getWidth() / 2, targetWidth);
                int newHeight = Math.max(current.getHeight() / 2, targetHeight);
                current = ScalingUtils.scaleImage(current, newWidth, newHeight, algorithm);
            }
            
            // Final scale to exact target
            return ScalingUtils.scaleImage(current, targetWidth, targetHeight, algorithm);
        } else {
            return ScalingUtils.scaleImage(src, targetWidth, targetHeight, algorithm);
        }
    }

    /**
     * Scale to fit within bounds while maintaining aspect ratio
     */
    public static BufferedImage scaleToFit(BufferedImage src, int maxWidth, int maxHeight, 
                                        io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm algorithm) {
        return ScalingUtils.scaleWithAspectRatio(src, maxWidth, maxHeight, algorithm);
    }

    /**
     * Scale to fill bounds (may crop) while maintaining aspect ratio
     */
    public static BufferedImage scaleToFill(BufferedImage src, int targetWidth, int targetHeight, 
                                        io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm algorithm) {
        double srcRatio = (double) src.getWidth() / src.getHeight();
        double targetRatio = (double) targetWidth / targetHeight;
        
        int scaleWidth, scaleHeight;
        
        if (srcRatio > targetRatio) {
            // Source is wider, scale by height
            scaleHeight = targetHeight;
            scaleWidth = (int) (targetHeight * srcRatio);
        } else {
            // Source is taller, scale by width
            scaleWidth = targetWidth;
            scaleHeight = (int) (targetWidth / srcRatio);
        }
        
        BufferedImage scaled = ScalingUtils.scaleImage(src, scaleWidth, scaleHeight, algorithm);
        
        // Crop to target size
        int cropX = (scaleWidth - targetWidth) / 2;
        int cropY = (scaleHeight - targetHeight) / 2;
        
        return scaled.getSubimage(cropX, cropY, targetWidth, targetHeight);
    }

    /**
     * Optimized scaling for power-of-2 sizes (useful for textures)
     */
    public static BufferedImage scaleToPowerOfTwo(BufferedImage src, ScalingAlgorithm algorithm) {
        int width = src.getWidth();
        int height = src.getHeight();
        
        int newWidth = MathHelpers.nextPowerOfTwo(width);
        int newHeight = MathHelpers.nextPowerOfTwo(height);
        
        if (newWidth == width && newHeight == height) {
            return src; // Already power of two
        }
        
        return ScalingUtils.scaleImage(src, newWidth, newHeight, algorithm);
    }

    /**
     * Scale with aspect ratio preservation
     */
    public static BufferedImage scaleWithAspectRatio(BufferedImage src, int maxWidth, int maxHeight, 
                                                    io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm algorithm) {
        Dimension scaled = ScalingUtils.getScaledDimension(
            new Dimension(src.getWidth(), src.getHeight()),
            new Dimension(maxWidth, maxHeight)
        );
        return ScalingUtils.scaleImage(src, scaled.width, scaled.height, algorithm);
    }

    /**
     * Scale image using specified algorithm with quality control
     */
    public static BufferedImage scaleImage(BufferedImage src, int targetWidth, int targetHeight, 
                                        io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm algorithm) {
        if (src == null || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Invalid source image or dimensions");
        }
        
        switch (algorithm) {
            case NEAREST_NEIGHBOR:
                return NearestNeighborScaling.scaleNearestNeighbor(src, targetWidth, targetHeight);
            case BICUBIC:
                return BicubicScaling.scaleBicubic(src, targetWidth, targetHeight);
            case AREA_AVERAGING:
                return AreaAverageScaling.scaleAreaAveraging(src, targetWidth, targetHeight);
            case LANCZOS:
                return LanczosScaling.scaleLanczos(src, targetWidth, targetHeight);
            case MITCHELL_NETRAVALI:
                return MitchellScaling.scaleMitchell(src, targetWidth, targetHeight);
            case BILINEAR:
            default:
                return BilinearScaling.scaleBilinear(src, targetWidth, targetHeight);
        }
    }

    public static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {
        
        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;
    
        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }
    
        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }
    
        return new Dimension(new_width, new_height);
    }

    public static BufferedImage scaleImage(BufferedImage src, int x1, int y1, int x2, int y2, 
        int targetWidth, int targetHeight, ScalingAlgorithm algorithm
    ) {
        
        targetWidth = Math.max(targetWidth, 1);
        targetHeight = Math.max(targetHeight, 1);
    
        switch (algorithm) {
            case NEAREST_NEIGHBOR:
                return NearestNeighborScaling.scaleNearestNeighborCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case BICUBIC:
                return BicubicScaling.scaleBicubicCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case AREA_AVERAGING:
                return AreaAverageScaling.scaleAreaAveraging(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case LANCZOS:
                return LanczosScaling.scaleLanczosCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case MITCHELL_NETRAVALI:
                return MitchellScaling.scaleMitchellCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case BILINEAR:
            default:
                return BilinearScaling.scaleBilinearCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
        }
    }
    
}
