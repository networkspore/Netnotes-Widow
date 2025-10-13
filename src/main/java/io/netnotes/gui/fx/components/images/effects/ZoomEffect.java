package io.netnotes.gui.fx.components.images.effects;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.utils.MathHelpers;
import io.netnotes.gui.fx.display.ImageHelpers;
import io.netnotes.gui.fx.display.LanczosScaling;
import io.netnotes.gui.fx.display.MitchellScaling;
import io.netnotes.gui.fx.display.ImageHelpers.ScalingAlgorithm;

public class ZoomEffect extends ImageEffects {
    public static BigDecimal MIN_ZOOM_AMOUNT = BigDecimal.valueOf(0.00001);
    public static BigDecimal MAX_ZOOM_AMOUNT = BigDecimal.valueOf(10000);

    public static String NAME = "ZOOM";

    private final ScalingAlgorithm m_algorithm; 
    private final BigDecimal m_amount; 
    private final BigDecimal m_centerX; 
    private final BigDecimal m_centerY;
    private final Rectangle m_crop;

    public ZoomEffect(ScalingAlgorithm algorithm, BigDecimal amount, BigDecimal centerX, BigDecimal centerY) {
        super(NAME);
        m_algorithm = algorithm;
        m_amount = amount;
        m_centerX = centerX;
        m_centerY = centerY;
        m_crop = null;
    }

    public ZoomEffect(NoteBytes id, ScalingAlgorithm algorithm, BigDecimal amount, BigDecimal centerX, BigDecimal centerY) {
        super(id, NAME);
        m_algorithm = algorithm;
        m_amount = amount;
        m_centerX = centerX;
        m_centerY = centerY;
        m_crop = null;
    }

    public ZoomEffect(ScalingAlgorithm algorithm, Rectangle rect) {
        super(NAME);
        m_algorithm = algorithm;
        m_amount = null;
        m_centerX = null;
        m_centerY = null;
        m_crop = rect;
    }

    public ZoomEffect(Rectangle rect) {
        this(ScalingAlgorithm.BILINEAR, rect);
    }

    public ZoomEffect(NoteBytes id, ScalingAlgorithm algorithm, BigDecimal amount) {
        this(id, algorithm,amount, null, null);
    }

    public ZoomEffect(NoteBytes id, BigDecimal amount) {
        this(id, ScalingAlgorithm.BILINEAR,amount, null, null);
    }

    public ZoomEffect(BigDecimal amount) {
        this(NoteUUID.createLocalUUID128(), amount);
    }

    @Override
    public void applyEffect(BufferedImage img) {
        if(m_crop == null){
            if(m_centerX != null && m_centerY != null){
                zoomRGB(img, m_algorithm, m_amount, MathHelpers.clampDimension(m_centerX, img.getWidth()), MathHelpers.clampDimension(m_centerY, img.getHeight()));
            }else{
                zoomRGB(img, m_algorithm, m_amount);
            }
        }else{
            zoomRGB(img,m_algorithm, m_crop);
        }
    }


    public static void zoomRGB(BufferedImage img, ScalingAlgorithm algorithm, Rectangle zoom) {
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        
        // Validate rectangle has positive dimensions
        if (zoom.width <= 0 || zoom.height <= 0) {
            throw new IllegalArgumentException("Zoom rectangle must have positive width and height");
        }
        
        // Calculate the center of the zoom rectangle
        BigDecimal centerX = BigDecimal.valueOf(zoom.x).add(
            MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(zoom.width), BigDecimal.TWO)
        );
        BigDecimal centerY = BigDecimal.valueOf(zoom.y).add(
            MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(zoom.height), BigDecimal.TWO)
        );
        
        // Determine if this is a zoom in or zoom out based on rectangle size
        boolean zoomingIn = zoom.width <= srcWidth && zoom.height <= srcHeight;
        
        if (zoomingIn) {
            // Zoom in: The rectangle defines what region to expand to fill the canvas
            // Clamp the rectangle to image bounds
            int x1 = Math.max(0, zoom.x);
            int y1 = Math.max(0, zoom.y);
            int x2 = Math.min(srcWidth, zoom.x + zoom.width);
            int y2 = Math.min(srcHeight, zoom.y + zoom.height);
            
            // Ensure valid crop region
            if (x2 <= x1 || y2 <= y1) {
                throw new IllegalArgumentException("Zoom rectangle does not intersect with image");
            }
            
            // Scale the crop region to fill the entire canvas
            BufferedImage scaled = scaleImage(img, x1, y1, x2, y2, srcWidth, srcHeight, algorithm);
            
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            
        } else {
            // Zoom out: The rectangle defines where the canvas should appear when scaled down
            // Calculate the scale factor needed to fit the canvas into the rectangle
            BigDecimal scaleX = MathHelpers.divideNearestNeighbor(
                BigDecimal.valueOf(zoom.width),
                BigDecimal.valueOf(srcWidth)
            );
            BigDecimal scaleY = MathHelpers.divideNearestNeighbor(
                BigDecimal.valueOf(zoom.height),
                BigDecimal.valueOf(srcHeight)
            );
            
            // Use the smaller scale to maintain aspect ratio (fit inside rectangle)
            BigDecimal scale = scaleX.compareTo(scaleY) < 0 ? scaleX : scaleY;
            
            // Calculate scaled dimensions
            int scaledWidth = MathHelpers.multiplyInt(scale, srcWidth);
            int scaledHeight = MathHelpers.multiplyInt(scale, srcHeight);
            
            // Ensure at least 1x1
            scaledWidth = Math.max(scaledWidth, 1);
            scaledHeight = Math.max(scaledHeight, 1);
            
            // Scale down the entire image
            BufferedImage scaled = scaleImage(img, 0, 0, srcWidth, srcHeight, 
                                            scaledWidth, scaledHeight, algorithm);
            
            // Calculate position to draw the scaled image so its center aligns with rectangle center
            int dx = centerX.subtract(
                MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(scaledWidth), BigDecimal.TWO)
            ).setScale(0, RoundingMode.HALF_EVEN).intValue();
            
            int dy = centerY.subtract(
                MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(scaledHeight), BigDecimal.TWO)
            ).setScale(0, RoundingMode.HALF_EVEN).intValue();
            
            // Clear canvas and draw scaled image
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, srcWidth, srcHeight);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(scaled, dx, dy, null);
            g.dispose();
        }
    }
   
    public static void zoomRGB(BufferedImage img, ScalingAlgorithm algorithm, BigDecimal amount) {
        int zoomDirection = amount.compareTo(BigDecimal.ONE);

        if(zoomDirection == 0){
            return;
        }

        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();

        // Clamp zoom amount
        amount = MathHelpers.clamp(amount, MIN_ZOOM_AMOUNT, MAX_ZOOM_AMOUNT);

        // Calculate crop size
        int targetWidth = MathHelpers.divideNearestNeighborToInt(BigDecimal.valueOf(srcWidth), amount);
        int targetHeight = MathHelpers.divideNearestNeighborToInt(BigDecimal.valueOf(srcHeight), amount);

        // Ensure crop size is at least 1x1
        targetWidth = Math.max(targetWidth, 1);     
        targetHeight = Math.max(targetHeight, 1);

        if(zoomDirection > 0){

            BufferedImage zoomed = scaleImage(img, 0, 0, srcWidth, srcHeight, targetWidth, targetHeight, algorithm);
            // Draw the zoomed image back onto the original image
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src); // replace all pixels
            g.drawImage(zoomed, 0, 0, null);
            g.dispose();
        }else{
            BufferedImage shrunk = ImageHelpers.scaleImage(img, targetWidth, targetHeight, algorithm);

            int drawX = MathHelpers.divideNearestNeighborToInt( BigDecimal.valueOf(srcWidth - targetWidth), BigDecimal.TWO);
            int drawY = MathHelpers.divideNearestNeighborToInt( BigDecimal.valueOf(srcHeight - targetHeight), BigDecimal.TWO);

            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src); // replace all pixels
            g.drawImage(shrunk, drawX, drawY, null);
            g.dispose();
        }
    }


    public static void zoomRGB(BufferedImage img, ScalingAlgorithm algorithm, BigDecimal amount, BigDecimal centerX, BigDecimal centerY) {
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        BigDecimal bigWidth = BigDecimal.valueOf(srcWidth);
        BigDecimal bigHeight = BigDecimal.valueOf(srcHeight);
        
        // Clamp zoom amount
        amount = MathHelpers.clamp(amount, MIN_ZOOM_AMOUNT, MAX_ZOOM_AMOUNT);
        
        BigDecimal cropWidth = MathHelpers.divideNearestNeighbor(bigWidth, amount);
        BigDecimal cropHeight = MathHelpers.divideNearestNeighbor(bigHeight, amount);
        
        // Ensure crop size is at least 1x1
        cropWidth = cropWidth.max(BigDecimal.ONE);
        cropHeight = cropHeight.max(BigDecimal.ONE);
        
        // Calculate crop bounds centered on focal point
        BigDecimal halfCropWidth = MathHelpers.divideNearestNeighbor(cropWidth, BigDecimal.TWO);
        BigDecimal halfCropHeight = MathHelpers.divideNearestNeighbor(cropHeight, BigDecimal.TWO);
        
        BigDecimal bigX1 = centerX.subtract(halfCropWidth);
        BigDecimal bigY1 = centerY.subtract(halfCropHeight);
        BigDecimal bigX2 = bigX1.add(cropWidth);
        BigDecimal bigY2 = bigY1.add(cropHeight);
        
        // Clamp to image bounds
        int x1 = bigX1.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_EVEN).intValue();
        int y1 = bigY1.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_EVEN).intValue();
        int x2 = bigX2.min(bigWidth).setScale(0, RoundingMode.HALF_EVEN).intValue();
        int y2 = bigY2.min(bigHeight).setScale(0, RoundingMode.HALF_EVEN).intValue();
        
        int cropW = x2 - x1;
        int cropH = y2 - y1;
        
        // Ensure at least 1x1
        cropW = Math.max(cropW, 1);
        cropH = Math.max(cropH, 1);  // Fixed: was cropW
        
        // Decide if scaling up or down
        boolean zoomingIn = cropW <= srcWidth && cropH <= srcHeight;
        
        int targetWidth = zoomingIn ? srcWidth : cropW;
        int targetHeight = zoomingIn ? srcHeight : cropH;
        
        // Final scale
        BufferedImage scaled = scaleImage(img, x1, y1, x2, y2, targetWidth, targetHeight, algorithm);
        
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear); // Clear canvas
        g.fillRect(0, 0, srcWidth, srcHeight);
        g.setComposite(AlphaComposite.SrcOver);
        
        if (zoomingIn) {
            // Draw scaled to full canvas
            g.drawImage(scaled, 0, 0, null);
        } else {
            // Offset so focal point stays at centerX, centerY
            BigDecimal halfTargetWidth = MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(targetWidth), BigDecimal.TWO);
            BigDecimal halfTargetHeight = MathHelpers.divideNearestNeighbor(BigDecimal.valueOf(targetHeight), BigDecimal.TWO);
            
            int dx = centerX.subtract(halfTargetWidth).setScale(0, RoundingMode.HALF_EVEN).intValue();
            int dy = centerY.subtract(halfTargetHeight).setScale(0, RoundingMode.HALF_EVEN).intValue();
            
            g.drawImage(scaled, dx, dy, null);
        }
        g.dispose();
    }

    public static BufferedImage scaleImage(BufferedImage src, int x1, int y1, int x2, int y2, 
        int targetWidth, int targetHeight, ScalingAlgorithm algorithm
    ) {
        
        targetWidth = Math.max(targetWidth, 1);
        targetHeight = Math.max(targetHeight, 1);

        switch (algorithm) {
            case NEAREST_NEIGHBOR:
                return scaleNearestNeighborCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case BICUBIC:
                return scaleBicubicCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case AREA_AVERAGING:
                return scaleAreaAveraging(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case LANCZOS:
                return LanczosScaling.scaleLanczosCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case MITCHELL_NETRAVALI:
                return MitchellScaling.scaleMitchellCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
            case BILINEAR:
            default:
                return scaleBilinearCrop(src, x1, y1, x2, y2, targetWidth, targetHeight);
        }
    }

    public static BufferedImage scaleNearestNeighborCrop(BufferedImage img, int x1, int y1, int x2, int y2, 
        int targetWidth, int targetHeight
    ) {
        // Ensure crop bounds are within the image
        x1 = Math.max(0, x1);
        y1 = Math.max(0, y1);
        x2 = Math.min(img.getWidth(), x2);
        y2 = Math.min(img.getHeight(), y2);

        int cropWidth = x2 - x1;
        int cropHeight = y2 - y1;

        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop bounds: zero or negative size.");
        }

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, img.getType());

        BigDecimal xRatio = MathHelpers.divideNearestNeighbor(cropWidth, targetWidth);
        BigDecimal yRatio = MathHelpers.divideNearestNeighbor(cropHeight, targetHeight);

        for (int y = 0; y < targetHeight; y++) {
            int srcY = y1 + MathHelpers.multiplyInt(yRatio, y);
            srcY = Math.min(srcY, img.getHeight() - 1);

            for (int x = 0; x < targetWidth; x++) {
                int srcX = x1 + MathHelpers.multiplyInt(xRatio, x);
                srcX = Math.min(srcX, img.getWidth() - 1);

                result.setRGB(x, y, img.getRGB(srcX, srcY));
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
                
                int rgb = bilinearInterpolate(
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
        int a = interpolateChannel(a00, a10, a01, a11, w00, w10, w01, w11);
        int r = interpolateChannel(r00, r10, r01, r11, w00, w10, w01, w11);
        int g = interpolateChannel(g00, g10, g01, g11, w00, w10, w01, w11);
        int b = interpolateChannel(b00, b10, b01, b11, w00, w10, w01, w11);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateChannel(int c00, int c10, int c01, int c11,
        BigDecimal w00, BigDecimal w10, BigDecimal w01, BigDecimal w11
    ) {
        BigDecimal result = BigDecimal.valueOf(c00).multiply(w00)
            .add(BigDecimal.valueOf(c10).multiply(w10))
            .add(BigDecimal.valueOf(c01).multiply(w01))
            .add(BigDecimal.valueOf(c11).multiply(w11));
        
        return result.setScale(0, RoundingMode.HALF_EVEN).intValue();
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
        int scaledWidth = MathHelpers.multiplyInt(scaleX, src.getWidth());
        int scaledHeight = MathHelpers.multiplyInt(scaleY, src.getHeight());
        
        // Calculate scaled offset
        int scaledOffsetX = MathHelpers.multiplyInt(scaleX.multiply(offsetX), 1);
        int scaledOffsetY = MathHelpers.multiplyInt(scaleY.multiply(offsetY), 1);
        
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
            return scaleBilinearCrop(src, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight);
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
                int srcX1 = cropX1 + MathHelpers.multiplyInt(xRatio, x);
                int srcY1 = cropY1 + MathHelpers.multiplyInt(yRatio, y);
                int srcX2 = cropX1 + MathHelpers.multiplyInt(xRatio, x + 1);
                int srcY2 = cropY1 + MathHelpers.multiplyInt(yRatio, y + 1);
                
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

    private static final BigDecimal halfThreshold = new BigDecimal("0.5");

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
                
                current = scaleImage(current, currentX1, currentY1, currentX2, currentY2, 
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
            return scaleImage(current, 0, 0, currentWidth, currentHeight, targetWidth, targetHeight, algorithm);
        } else {
            // Small scale or upscaling — single pass
            return scaleImage(src, cropX1, cropY1, cropX2, cropY2, targetWidth, targetHeight, algorithm);
        }
    }
}
