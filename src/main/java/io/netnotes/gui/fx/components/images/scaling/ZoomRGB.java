package io.netnotes.gui.fx.components.images.scaling;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

import io.netnotes.engine.utils.MathHelpers;
import io.netnotes.gui.fx.components.images.effects.ZoomEffect;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils.ScalingAlgorithm;

public class ZoomRGB {

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
            BufferedImage scaled = ScalingUtils.scaleImage(img, x1, y1, x2, y2, srcWidth, srcHeight, algorithm);
            
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
            int scaledWidth = MathHelpers.multiplyLong(scale, srcWidth).setScale(0, RoundingMode.CEILING).intValue();
            int scaledHeight = MathHelpers.multiplyLong(scale, srcHeight).setScale(0, RoundingMode.CEILING).intValue();
            
            // Ensure at least 1x1
            scaledWidth = Math.max(scaledWidth, 1);
            scaledHeight = Math.max(scaledHeight, 1);
            
            // Scale down the entire image
            BufferedImage scaled = ScalingUtils.scaleImage(img, 0, 0, srcWidth, srcHeight, 
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
        amount = MathHelpers.clamp(amount, ZoomEffect.MIN_ZOOM_AMOUNT, ZoomEffect.MAX_ZOOM_AMOUNT);
    
        // Calculate crop size
        int targetWidth = MathHelpers.divideNearestNeighborToInt(BigDecimal.valueOf(srcWidth), amount);
        int targetHeight = MathHelpers.divideNearestNeighborToInt(BigDecimal.valueOf(srcHeight), amount);
    
        // Ensure crop size is at least 1x1
        targetWidth = Math.max(targetWidth, 1);     
        targetHeight = Math.max(targetHeight, 1);
    
        if(zoomDirection > 0){
    
            BufferedImage zoomed = ScalingUtils.scaleImage(img, 0, 0, srcWidth, srcHeight, targetWidth, targetHeight, algorithm);
            // Draw the zoomed image back onto the original image
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src); // replace all pixels
            g.drawImage(zoomed, 0, 0, null);
            g.dispose();
        }else{
            BufferedImage shrunk = ScalingUtils.scaleImage(img, targetWidth, targetHeight, algorithm);
    
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
        amount = MathHelpers.clamp(amount, ZoomEffect.MIN_ZOOM_AMOUNT, ZoomEffect.MAX_ZOOM_AMOUNT);
        
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
        BufferedImage scaled = ScalingUtils.scaleImage(img, x1, y1, x2, y2, targetWidth, targetHeight, algorithm);
        
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
    
}
