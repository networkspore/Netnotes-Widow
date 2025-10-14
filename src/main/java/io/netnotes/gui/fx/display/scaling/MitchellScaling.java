package io.netnotes.gui.fx.display.scaling;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import io.netnotes.engine.utils.MathHelpers;

public class MitchellScaling {
    
    // Mitchell-Netravali parameters
    // B + 2C = 1 for the one-parameter family
    // Standard Mitchell values: , C = 1/3 (balanced)
    // B = 1, C = 0 gives cubic B-spline (blurrier)
    // B = 0, C = 0.5 gives Catmull-Rom (sharper)
    private static final BigDecimal B_SPLINE = MathHelpers.ONE_THIRD;
    private static final BigDecimal C_SPLINE = MathHelpers.ONE_THIRD;
    
    // Support radius for Mitchell-Netravali is 2
    private static final BigDecimal MITCHELL_SUPPORT = BigDecimal.TWO;
    
    // MathContext for BigDecimal calculations
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    
    
    /**
     * Scale an image using Mitchell-Netravali resampling for balanced quality.
     * 
     * @param src Source image
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with Mitchell-Netravali interpolation
     */
    public static BufferedImage scaleMitchell(BufferedImage src, int targetWidth, int targetHeight){
        return scaleMitchell(src, B_SPLINE, C_SPLINE, MC, targetWidth, targetHeight);
    }

    /**
     * Scale an image using Mitchell-Netravali
     * 
     * @param src Source image
     * @param bSpline B = 1/3 = balanced. 0 sharper, 1 blurrier, 
     * @param cSpline C = 1/3 = balanced, 0.5 sharper, 0 blurrier
     * @param MathContext Precision and rounding
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with Mitchell-Netravali interpolation
     */
    public static BufferedImage scaleMitchell(BufferedImage src, BigDecimal bSpline, BigDecimal cSpline, MathContext mc, int targetWidth, int targetHeight) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, 
                                                 BufferedImage.TYPE_INT_ARGB);
        
        // Calculate scaling ratios using BigDecimal for precision
        BigDecimal xRatio = MathHelpers.divideMC(
            BigDecimal.valueOf(srcWidth),
            BigDecimal.valueOf(targetWidth),
            mc
        );
        BigDecimal yRatio = MathHelpers.divideMC(
            BigDecimal.valueOf(srcHeight),
            BigDecimal.valueOf(targetHeight),
            mc
        );
        
        // Process each pixel in the target image
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // Calculate the corresponding position in source image
                BigDecimal srcXDecimal = BigDecimal.valueOf(x).multiply(xRatio);
                BigDecimal srcYDecimal = BigDecimal.valueOf(y).multiply(yRatio);
                
                // Get the interpolated color
                int rgb = mitchellInterpolate(src, bSpline, cSpline, mc, srcXDecimal, srcYDecimal, xRatio, yRatio);
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }

    /**
     * Scale a cropped region of an image using Mitchell-Netravali 
     * resampling with balanced parameters
     * 
     * @param src Source image
     * @param cropX1 Left edge of crop region
     * @param cropY1 Top edge of crop region
     * @param cropX2 Right edge of crop region
     * @param cropY2 Bottom edge of crop region
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with Mitchell-Netravali interpolation
     */
    public static BufferedImage scaleMitchellCrop(BufferedImage src, int cropX1, int cropY1, int cropX2, int cropY2, 
        int targetWidth, int targetHeight
    ) {
        return scaleMitchellCrop(src, B_SPLINE, C_SPLINE, MC, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight);
    }
    
    /**
     * Scale a cropped region of an image using Mitchell-Netravali resampling.
     * 
     * @param src Source image
     * @param bSpline B = 1/3 = balanced. 0 sharper, 1 blurrier, 
     * @param cSpline C = 1/3 = balanced, 0.5 sharper, 0 blurrier
     * @param MathContext Precision and rounding
     * @param cropX1 Left edge of crop region
     * @param cropY1 Top edge of crop region
     * @param cropX2 Right edge of crop region
     * @param cropY2 Bottom edge of crop region
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Scaled image with Mitchell-Netravali interpolation
     */
    public static BufferedImage scaleMitchellCrop(BufferedImage src, BigDecimal bSpline, BigDecimal cSpline, MathContext mc,
        int cropX1, int cropY1, int cropX2, int cropY2, int targetWidth, int targetHeight
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
                int rgb = mitchellInterpolateCrop(src,bSpline, cSpline, mc, srcXDecimal, srcYDecimal, 
                                                 xRatio, yRatio, 
                                                 cropX1, cropY1, cropX2, cropY2);
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }
    
    /**
     * Perform Mitchell-Netravali interpolation at a specific point in the source image.
     * 
     * @param src Source image
     * @param x X coordinate in source image (can be fractional)
     * @param y Y coordinate in source image (can be fractional)
     * @param xRatio Horizontal scaling ratio
     * @param yRatio Vertical scaling ratio
     * @return Interpolated RGB color value
     */
    private static int mitchellInterpolate(BufferedImage src,BigDecimal bSpline, BigDecimal cSpline, MathContext mc, BigDecimal x, BigDecimal y,
                                          BigDecimal xRatio, BigDecimal yRatio) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // Center position
        int centerX = x.intValue();
        int centerY = y.intValue();
        
        // Calculate the support radius
        BigDecimal xSupport = xRatio.max(BigDecimal.ONE).multiply(MITCHELL_SUPPORT);
        BigDecimal ySupport = yRatio.max(BigDecimal.ONE).multiply(MITCHELL_SUPPORT);
        
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
            BigDecimal yWeight = mitchellKernel(yDist.divide(yRatio.max(BigDecimal.ONE), mc), bSpline, cSpline, mc);
            
            // Skip if weight is negligible
            if (yWeight.compareTo(BigDecimal.ZERO) == 0) continue;
            
            for (int dx = -xRadius; dx <= xRadius; dx++) {
                int sx = centerX + dx;
                
                // Skip if outside image bounds
                if (sx < 0 || sx >= srcWidth) continue;
                
                // Calculate X weight
                BigDecimal xDist = x.subtract(BigDecimal.valueOf(sx)).abs();
                BigDecimal xWeight = mitchellKernel(xDist.divide(xRatio.max(BigDecimal.ONE), mc), bSpline, cSpline, mc);
                
                // Skip if weight is negligible
                if (xWeight.compareTo(BigDecimal.ZERO) == 0) continue;
                
                // Combined weight
                BigDecimal weight = xWeight.multiply(yWeight, mc);
                
                // Get pixel color
                int rgb = src.getRGB(sx, sy);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Accumulate weighted values
                totalA = totalA.add(BigDecimal.valueOf(a).multiply(weight, mc));
                totalR = totalR.add(BigDecimal.valueOf(r).multiply(weight, mc));
                totalG = totalG.add(BigDecimal.valueOf(g).multiply(weight, mc));
                totalB = totalB.add(BigDecimal.valueOf(b).multiply(weight, mc));
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
     * Perform Mitchell-Netravali interpolation at a specific point, constrained to crop bounds.
     * 
     * @param src Source image
     * @param bSpline B = 1/3 = balanced. 0 sharper, 1 blurrier, 
     * @param cSpline C = 1/3 = balanced, 0.5 sharper, 0 blurrier
     * @param MathContext Precision and rounding
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
    private static int mitchellInterpolateCrop(BufferedImage src,BigDecimal bSpline, BigDecimal cSpline, MathContext mc,
                                               BigDecimal x, BigDecimal y,
                                               BigDecimal xRatio, BigDecimal yRatio,
                                               int cropX1, int cropY1, 
                                               int cropX2, int cropY2) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // Center position
        int centerX = x.intValue();
        int centerY = y.intValue();
        
        // Calculate the support radius
        BigDecimal xSupport = xRatio.max(BigDecimal.ONE).multiply(MITCHELL_SUPPORT);
        BigDecimal ySupport = yRatio.max(BigDecimal.ONE).multiply(MITCHELL_SUPPORT);
        
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
            BigDecimal yWeight = mitchellKernel(yDist.divide(yRatio.max(BigDecimal.ONE), mc), bSpline, cSpline, mc);
            
            // Skip if weight is negligible
            if (yWeight.compareTo(BigDecimal.ZERO) == 0) continue;
            
            for (int dx = -xRadius; dx <= xRadius; dx++) {
                int sx = centerX + dx;
                
                // Clamp to crop bounds and image bounds
                if (sx < cropX1 || sx >= cropX2 || sx < 0 || sx >= srcWidth) continue;
                
                // Calculate X weight
                BigDecimal xDist = x.subtract(BigDecimal.valueOf(sx)).abs();
                BigDecimal xWeight = mitchellKernel(xDist.divide(xRatio.max(BigDecimal.ONE), mc), bSpline, cSpline, mc);
                
                // Skip if weight is negligible
                if (xWeight.compareTo(BigDecimal.ZERO) == 0) continue;
                
                // Combined weight
                BigDecimal weight = xWeight.multiply(yWeight, mc);
                
                // Get pixel color
                int rgb = src.getRGB(sx, sy);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Accumulate weighted values
                totalA = totalA.add(BigDecimal.valueOf(a).multiply(weight, mc));
                totalR = totalR.add(BigDecimal.valueOf(r).multiply(weight, mc));
                totalG = totalG.add(BigDecimal.valueOf(g).multiply(weight, mc));
                totalB = totalB.add(BigDecimal.valueOf(b).multiply(weight, mc));
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
        int fallbackX = Math.min(Math.max(centerX, cropX1), cropX2 - 1);
        int fallbackY = Math.min(Math.max(centerY, cropY1), cropY2 - 1);
        return src.getRGB(fallbackX, fallbackY);
    }
    
    /**
     * Mitchell-Netravali kernel function using BigDecimal for precision.
     * 
     * The Mitchell-Netravali filter is defined as:
     * For |x| < 1:
     *   p(x) = (1/6) * ((12 - 9B - 6C)|x|³ + (-18 + 12B + 6C)|x|² + (6 - 2B))
     * For 1 ≤ |x| < 2:
     *   p(x) = (1/6) * ((-B - 6C)|x|³ + (6B + 30C)|x|² + (-12B - 48C)|x| + (8B + 24C))
     * For |x| ≥ 2:
     *   p(x) = 0
     * 
     * @param x Distance from center
     * @return Kernel weight
     */
    private static BigDecimal mitchellKernel(BigDecimal x,BigDecimal bSpline, BigDecimal cSpline, MathContext mc) {
        BigDecimal absX = x.abs();
        
        // Return 0 if outside support (|x| >= 2)
        if (absX.compareTo(MITCHELL_SUPPORT) >= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x2 = absX.multiply(absX, mc);  // x²
        BigDecimal x3 = x2.multiply(absX, mc);     // x³
        
        if (absX.compareTo(BigDecimal.ONE) < 0) {
            // For |x| < 1:
            // (1/6) * ((12 - 9B - 6C)x³ + (-18 + 12B + 6C)x² + (6 - 2B))
            
            BigDecimal coef3 = MathHelpers.TWELVE.subtract(bSpline.multiply(new BigDecimal("9"), mc))
                                     .subtract(bSpline.multiply(new BigDecimal("6"), mc));
            BigDecimal coef2 = new BigDecimal("-18").add(bSpline.multiply(MathHelpers.TWELVE, mc))
                                                     .add(cSpline.multiply(new BigDecimal("6"), mc));
            BigDecimal coef0 = new BigDecimal("6").subtract(bSpline.multiply(BigDecimal.TWO, mc));
            
            BigDecimal result = coef3.multiply(x3, mc)
                                     .add(coef2.multiply(x2, mc))
                                     .add(coef0)
                                     .multiply(MathHelpers.ONE_SIXTH, mc);
            
            return result.stripTrailingZeros();
        } else {
            // For 1 ≤ |x| < 2:
            // (1/6) * ((-b - 6C)x³ + (6B + 30C)x² + (-12B - 48C)x + (8B + 24C))
            
            BigDecimal coef3 = bSpline.negate().subtract(cSpline.multiply(MathHelpers.SIX, mc));
            BigDecimal coef2 = bSpline.multiply(MathHelpers.SIX, mc)
                                .add(cSpline.multiply(MathHelpers.THIRTY, mc));
            BigDecimal coef1 = bSpline.multiply(MathHelpers.TWELVE, mc).negate()
                                .subtract(cSpline.multiply(MathHelpers.FOURTY_EIGHT, mc));
            BigDecimal coef0 = bSpline.multiply(MathHelpers.EIGHT, mc)
                                .add(cSpline.multiply(MathHelpers.TWENTY_FOUR, mc));
            
            BigDecimal result = coef3.multiply(x3, mc)
                                     .add(coef2.multiply(x2, mc))
                                     .add(coef1.multiply(absX, mc))
                                     .add(coef0)
                                     .multiply(MathHelpers.ONE_SIXTH, mc);
            
            return result.stripTrailingZeros();
        }
    }
    
    /**
     * Clamp a color channel value to the valid range [0, 255].
     */
    private static int clampColorChannel(BigDecimal value) {
        int intValue = value.setScale(0, RoundingMode.HALF_EVEN).intValue();
        return Math.max(0, Math.min(255, intValue));
    }
}