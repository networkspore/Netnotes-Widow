package io.netnotes.gui.fx.components.images.scaling;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

import io.netnotes.engine.utils.MathHelpers;

public class NearestNeighborScaling {

    public static BufferedImage scaleNearestNeighbor(BufferedImage src, int width, int height) {
        int type = (src.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        final BufferedImage bufImage = new BufferedImage(width, height, type);
        final Graphics2D g2 = bufImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
    
        return bufImage;
    }

    public static BufferedImage scaleNearestNeighbor(BufferedImage buf, int width, int height, boolean maintainRatio){
        if(!maintainRatio){
            return scaleNearestNeighbor(buf, width, height);  
        }
        if(buf.getWidth() == buf.getHeight()){
            int size = width < height ? width : height;
    
            return scaleNearestNeighbor(buf, size, size);
        }else{
            double wR = buf.getWidth() / width;
            double hR = buf.getHeight() / height;
            boolean gT = wR > 1 || hR > 1;
    
             if(gT ? wR < hR : wR > hR){
                wR = 1 /wR;
                return scaleNearestNeighbor(buf, (int)(wR * buf.getWidth()), (int)(wR * buf.getHeight()));
             }else{
                hR = 1 / hR;
                return scaleNearestNeighbor(buf, (int)(hR * buf.getWidth()), (int)(hR * buf.getHeight()));
             }
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
            int srcY = y1 + MathHelpers.multiplyToInt(yRatio, y);
            srcY = Math.min(srcY, img.getHeight() - 1);
    
            for (int x = 0; x < targetWidth; x++) {
                int srcX = x1 + MathHelpers.multiplyToInt(xRatio, x);
                srcX = Math.min(srcX, img.getWidth() - 1);
    
                result.setRGB(x, y, img.getRGB(srcX, srcY));
            }
        }
    
        return result;
    }

    
}
