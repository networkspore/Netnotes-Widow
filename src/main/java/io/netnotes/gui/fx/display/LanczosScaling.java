package io.netnotes.gui.fx.display;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import io.netnotes.engine.utils.MathHelpers;

public class LanczosScaling {
    
    // Lanczos kernel size (a=3 is standard, a=2 is faster but lower quality)
    public static final BigDecimal LANCZOS_A = BigDecimal.valueOf(3);
    public static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
   
    public static BufferedImage scaleLanczos(BufferedImage src, int targetWidth, int targetHeight){
        return scaleLanczos(src, LANCZOS_A, MC, targetWidth, targetHeight);
    }
    /**
     * Scale an image using Lanczos resampling for high quality results.
     * This is the pure version without cropping.
     * 
     * @param src Source image
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with high quality Lanczos interpolation
     */
    public static BufferedImage scaleLanczos(BufferedImage src, BigDecimal kernelSize, MathContext mc, int targetWidth, int targetHeight) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        
        // Calculate scaling ratios using BigDecimal for precision
        BigDecimal xRatio = MathHelpers.divideMC(BigDecimal.valueOf(srcWidth), BigDecimal.valueOf(targetWidth), mc);
        BigDecimal yRatio = MathHelpers.divideMC(BigDecimal.valueOf(srcHeight), BigDecimal.valueOf(targetHeight), mc);
        
        // Process each pixel in the target image
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // Calculate the corresponding position in source image
                BigDecimal srcXDecimal = BigDecimal.valueOf(x).multiply(xRatio);
                BigDecimal srcYDecimal = BigDecimal.valueOf(y).multiply(yRatio);
                
                // Get the interpolated color
                int rgb = lanczosInterpolate(src, kernelSize, mc, srcXDecimal, srcYDecimal, xRatio, yRatio);
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }
    
    /**
     * Perform Lanczos interpolation at a specific point in the source image.
     * 
     * @param src Source image
     * @param x X coordinate in source image (can be fractional)
     * @param y Y coordinate in source image (can be fractional)
     * @param xRatio Horizontal scaling ratio
     * @param yRatio Vertical scaling ratio
     * @return Interpolated RGB color value
     */
    private static int lanczosInterpolate(BufferedImage src, BigDecimal kernelSize, MathContext mc, BigDecimal x, BigDecimal y,
        BigDecimal xRatio, BigDecimal yRatio
    ) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // Center position
        int centerX = x.intValue();
        int centerY = y.intValue();
        
        // Calculate the support radius (how many pixels to sample)
        // When downscaling, we need to sample more pixels
        BigDecimal xSupport = xRatio.max(BigDecimal.ONE).multiply(kernelSize);
        BigDecimal ySupport = yRatio.max(BigDecimal.ONE).multiply(kernelSize);
        
        int xRadius = xSupport.setScale(0, RoundingMode.CEILING).intValue();
        int yRadius = ySupport.setScale(0, RoundingMode.CEILING).intValue();
        
        // Accumulate weighted color values
        BigDecimal totalR = BigDecimal.ZERO;
        BigDecimal totalG = BigDecimal.ZERO;
        BigDecimal totalB = BigDecimal.ZERO;
        BigDecimal totalA = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        // Sample surrounding pixels
        for (int dy = -yRadius; dy <= yRadius; dy++) {
            int sy = centerY + dy;
            
            // Skip if outside image bounds
            if (sy < 0 || sy >= srcHeight) continue;
            
            // Calculate Y weight
            BigDecimal yDist = y.subtract(BigDecimal.valueOf(sy)).abs();
            BigDecimal yWeight = lanczosKernel(kernelSize,mc, yDist.divide(yRatio.max(BigDecimal.ONE), mc));
            
            // Skip if weight is negligible
            if (yWeight.compareTo(BigDecimal.ZERO) == 0) continue;
            
            for (int dx = -xRadius; dx <= xRadius; dx++) {
                int sx = centerX + dx;
                
                // Skip if outside image bounds
                if (sx < 0 || sx >= srcWidth) continue;
                
                // Calculate X weight
                BigDecimal xDist = x.subtract(BigDecimal.valueOf(sx)).abs();
                BigDecimal xWeight = lanczosKernel(kernelSize, mc, xDist.divide(xRatio.max(BigDecimal.ONE), mc));
                
                // Skip if weight is negligible
                if (xWeight.compareTo(BigDecimal.ZERO) == 0) continue;
                
                // Combined weight
                BigDecimal weight = xWeight.multiply(yWeight);
                
                // Get pixel color
                int rgb = src.getRGB(sx, sy);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Accumulate weighted values
                totalA = totalA.add(BigDecimal.valueOf(a).multiply(weight));
                totalR = totalR.add(BigDecimal.valueOf(r).multiply(weight));
                totalG = totalG.add(BigDecimal.valueOf(g).multiply(weight));
                totalB = totalB.add(BigDecimal.valueOf(b).multiply(weight));
                totalWeight = totalWeight.add(weight);
            }
        }
        
        // Normalize by total weight
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            int a = clampColorChannel(totalA.divide(totalWeight, mc));
            int r = clampColorChannel(totalR.divide(totalWeight, mc));
            int g = clampColorChannel(totalG.divide(totalWeight, mc));
            int b = clampColorChannel(totalB.divide(totalWeight, mc));
            
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        // Fallback to nearest neighbor if no valid samples
        return src.getRGB(
            Math.min(Math.max(centerX, 0), srcWidth - 1),
            Math.min(Math.max(centerY, 0), srcHeight - 1)
        );
    }
    
    /**
     * High-precision Lanczos kernel using BigDecimal math:
     * L(x) = sinc(x) * sinc(x / a)
     * where sinc(t) = sin(πt) / (πt)
     * 
     * @param x Distance from center
     * @return Kernel weight
     */
    public static BigDecimal lanczosKernel(BigDecimal kernelSize, MathContext mc, BigDecimal x) {
        BigDecimal absX = x.abs();

        // Return 0 if outside support (|x| >= a)
        if (absX.compareTo(kernelSize) >= 0) {
            return BigDecimal.ZERO;
        }

        // Handle x = 0 case (sinc(0) = 1)
        if (x.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        // Compute πx and sin(πx)
        BigDecimal pi = MathHelpers.pi(mc);
        BigDecimal piX = pi.multiply(x, mc);
        BigDecimal sinPiX = MathHelpers.sin(piX, mc);
        BigDecimal sincX = sinPiX.divide(piX, mc);

        // Compute πx/a and sin(πx/a)
        BigDecimal xOverA = x.divide(kernelSize, mc);
        BigDecimal piXOverA = pi.multiply(xOverA, mc);
        BigDecimal sinPiXOverA = MathHelpers.sin(piXOverA, mc);
        BigDecimal sincXOverA = sinPiXOverA.divide(piXOverA, mc);

        // L(x) = sinc(x) * sinc(x/a)
        return sincX.multiply(sincXOverA, mc).stripTrailingZeros();
    }
    
    /**
     * Clamp a color channel value to the valid range [0, 255].
     */
    private static int clampColorChannel(BigDecimal value) {
        int intValue = value.setScale(0, RoundingMode.HALF_EVEN).intValue();
        return Math.max(0, Math.min(255, intValue));
    }

     public static BufferedImage scaleLanczosCrop(BufferedImage src,  int cropX1, int cropY1,  int cropX2, int cropY2,
        int targetWidth, int targetHeight
    ) {
        return scaleLanczosCrop(src, LANCZOS_A, MC, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight);
    }

     /**
     * Scale a cropped region of an image using Lanczos resampling.
     * 
     * @param src Source image
     * @param cropX1 Left edge of crop region
     * @param cropY1 Top edge of crop region
     * @param cropX2 Right edge of crop region
     * @param cropY2 Bottom edge of crop region
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with high quality Lanczos interpolation
     */
     public static BufferedImage scaleLanczosCrop(BufferedImage src, BigDecimal kernelSize, MathContext mc, int cropX1, int cropY1,  int cropX2, int cropY2,
        int targetWidth, int targetHeight
    ) {
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
        
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, 
                                                 BufferedImage.TYPE_INT_ARGB);
        
        // Calculate scaling ratios using BigDecimal for precision
        BigDecimal xRatio = MathHelpers.divideMC(
            BigDecimal.valueOf(cropWidth),
            BigDecimal.valueOf(targetWidth),
            mc
        );
        BigDecimal yRatio = MathHelpers.divideMC(
            BigDecimal.valueOf(cropHeight),
            BigDecimal.valueOf(targetHeight),
            mc
        );
        
        // Process each pixel in the target image
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // Calculate the corresponding position in the crop region
                BigDecimal srcXDecimal = BigDecimal.valueOf(cropX1).add(
                    BigDecimal.valueOf(x).multiply(xRatio)
                );
                BigDecimal srcYDecimal = BigDecimal.valueOf(cropY1).add(
                    BigDecimal.valueOf(y).multiply(yRatio)
                );
                
                // Get the interpolated color
                int rgb = lanczosInterpolateCrop(src, kernelSize, mc, srcXDecimal, srcYDecimal, 
                                                xRatio, yRatio, 
                                                cropX1, cropY1, cropX2, cropY2);
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }

    /**
     * Perform Lanczos interpolation at a specific point, constrained to crop bounds.
     * 
     * @param src Source image
     * @param x X coordinate in source image (can be fractional)
     * @param y Y coordinate in source image (can be fractional)
     * @param xRatio Horizontal scaling ratio
     * @param yRatio Vertical scaling ratio
     * @param cropX1 Left edge of crop region
     * @param cropY1 Top edge of crop region
     * @param cropX2 Right edge of crop region
     * @param cropY2 Bottom edge of crop region
     * @return Interpolated RGB color value
     */
    private static int lanczosInterpolateCrop(BufferedImage src, BigDecimal kernelSize, MathContext mc, BigDecimal x, BigDecimal y, BigDecimal xRatio, 
        BigDecimal yRatio, int cropX1, int cropY1, int cropX2, int cropY2
    ) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // Center position
        int centerX = x.intValue();
        int centerY = y.intValue();
        
        // Calculate the support radius (how many pixels to sample)
        // When downscaling, we need to sample more pixels
        BigDecimal xSupport = xRatio.max(BigDecimal.ONE).multiply(kernelSize);
        BigDecimal ySupport = yRatio.max(BigDecimal.ONE).multiply(kernelSize);
        
        int xRadius = xSupport.setScale(0, RoundingMode.CEILING).intValue();
        int yRadius = ySupport.setScale(0, RoundingMode.CEILING).intValue();
        
        // Accumulate weighted color values
        BigDecimal totalR = BigDecimal.ZERO;
        BigDecimal totalG = BigDecimal.ZERO;
        BigDecimal totalB = BigDecimal.ZERO;
        BigDecimal totalA = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        // Sample surrounding pixels
        for (int dy = -yRadius; dy <= yRadius; dy++) {
            int sy = centerY + dy;
            
            // Clamp to crop bounds and image bounds
            if (sy < cropY1 || sy >= cropY2 || sy < 0 || sy >= srcHeight) continue;
            
            // Calculate Y weight
            BigDecimal yDist = y.subtract(BigDecimal.valueOf(sy)).abs();
            BigDecimal yWeight = lanczosKernel(kernelSize, mc, yDist.divide(yRatio.max(BigDecimal.ONE), mc));
            
            // Skip if weight is negligible
            if (yWeight.compareTo(BigDecimal.ZERO) == 0) continue;
            
            for (int dx = -xRadius; dx <= xRadius; dx++) {
                int sx = centerX + dx;
                
                // Clamp to crop bounds and image bounds
                if (sx < cropX1 || sx >= cropX2 || sx < 0 || sx >= srcWidth) continue;
                
                // Calculate X weight
                BigDecimal xDist = x.subtract(BigDecimal.valueOf(sx)).abs();
                BigDecimal xWeight = lanczosKernel(kernelSize, mc, xDist.divide(xRatio.max(BigDecimal.ONE), mc));
                
                // Skip if weight is negligible
                if (xWeight.compareTo(BigDecimal.ZERO) == 0) continue;
                
                // Combined weight
                BigDecimal weight = xWeight.multiply(yWeight);
                
                // Get pixel color
                int rgb = src.getRGB(sx, sy);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Accumulate weighted values
                totalA = totalA.add(BigDecimal.valueOf(a).multiply(weight));
                totalR = totalR.add(BigDecimal.valueOf(r).multiply(weight));
                totalG = totalG.add(BigDecimal.valueOf(g).multiply(weight));
                totalB = totalB.add(BigDecimal.valueOf(b).multiply(weight));
                totalWeight = totalWeight.add(weight);
            }
        }
        
        // Normalize by total weight
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            int a = clampColorChannel(totalA.divide(totalWeight, mc));
            int r = clampColorChannel(totalR.divide(totalWeight, mc));
            int g = clampColorChannel(totalG.divide(totalWeight, mc));
            int b = clampColorChannel(totalB.divide(totalWeight, mc));
            
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        // Fallback to nearest neighbor if no valid samples (shouldn't happen with proper bounds)
        int fallbackX = Math.min(Math.max(centerX, cropX1), cropX2 - 1);
        int fallbackY = Math.min(Math.max(centerY, cropY1), cropY2 - 1);
        return src.getRGB(fallbackX, fallbackY);
    }
}