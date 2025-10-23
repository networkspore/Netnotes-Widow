package io.netnotes.gui.fx.noteBytes;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.gui.fx.display.ImageHelpers;
import io.netnotes.gui.fx.display.ImageHelpers.ImageFormat;

public class NoteBytesImage extends NoteBytes {
    private BufferedImage m_bufferedImageCache = null;
    private boolean m_cacheEnabled = true;
    
    // Image metadata (cached on first decode)
    private Integer m_cachedWidth = null;
    private Integer m_cachedHeight = null;
    private String m_cachedFormat = null;

    private String m_hashId = null;

    public NoteBytesImage(byte[] bytes) {
        this(bytes, true);
    }

    public NoteBytesImage(byte[] bytes, boolean enableCache){
        super(bytes, NoteBytesMetaData.IMAGE_TYPE);
        m_cacheEnabled = enableCache;
        m_cachedFormat = ImageHelpers.detectImageFormat(bytes);
        if(enableCache && !m_cachedFormat.equals(ImageFormat.UNKNOWN.getValue())){
            try{
                m_bufferedImageCache = getAsBufferedImage();
                m_cachedWidth = m_bufferedImageCache.getWidth();
                m_cachedHeight = m_bufferedImageCache.getHeight();
                
            }catch(IOException e){
                System.err.println("Warning: noteBytesImage decoding failed");
            }
        }
        m_hashId = getHashId();
    }

    public NoteBytesImage(NoteBytes noteBytes) {
        super(noteBytes.get(), NoteBytesMetaData.IMAGE_TYPE);
        m_hashId = getHashId();
    }
    
    public NoteBytesImage(BufferedImage image, String format) throws IOException {
        super(ImageHelpers.encodeImage(image, format), NoteBytesMetaData.IMAGE_TYPE);
        m_bufferedImageCache = image;
        m_cachedWidth = image.getWidth();
        m_cachedHeight = image.getHeight();
        m_cachedFormat = format.toUpperCase();
        m_hashId = getHashId();
    }

    public static NoteBytesImage of(Image image) throws IOException{
        return new NoteBytesImage(ImageHelpers.encodeImage(image, ImageFormat.PNG.getValue()));
    }

    public static NoteBytesImage of(Image image, String encoding) throws IOException{
        return new NoteBytesImage(ImageHelpers.encodeImage(image, encoding));
    }

    public static NoteBytesImage of(BufferedImage image, String encoding) throws IOException{
        return new NoteBytesImage(ImageHelpers.encodeImage(image, encoding));
    }

    public String getHashId(){
        byte[] bytes = get();
        if(m_hashId == null){
            if(bytes.length > 0){
                m_hashId = EncodingHelpers.encodeUrlSafeString(HashServices.digestBytesToBytes(bytes, 16));
            }else{
                m_hashId = "NONE";
            }
        }
        return m_hashId;
    }


    public boolean isCached(){
        return m_bufferedImageCache != null;
    }

    public BufferedImage getBufferedImageCache(){
        return m_bufferedImageCache;
    }


    public BufferedImage getAsBufferedImage() throws IOException{
        return getAsBufferedImage(m_cacheEnabled);
    }

    /**
     * Get BufferedImage with optional caching
     * 
     * @param useCache If true, cache the decoded image for subsequent calls
     * @return BufferedImage or null if data is invalid
     * @throws IOException If image decoding fails
     */
    public BufferedImage getAsBufferedImage(boolean useCache) throws IOException {
        byte[] bytes = get();
        
        // Return cached image if available
        if (useCache && m_bufferedImageCache != null) {
            return m_bufferedImageCache;
        }
        
        // Validate data
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        
        // Decode image
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        
        if (image == null) {
            throw new IOException("Failed to decode image - unsupported format or corrupted data");
        }
        
        // Cache metadata
        m_cachedWidth = image.getWidth();
        m_cachedHeight = image.getHeight();
        m_cachedFormat = ImageHelpers.detectImageFormat(bytes);
        
        // Cache image if requested
        if (useCache) {
            m_bufferedImageCache = image;
        }
        
        return image;
    }
    
    /**
     * Get cached image without decoding (returns null if not cached)
     */
    public BufferedImage getCachedImage() {
        return m_bufferedImageCache;
    }

    public void setCachedImage(BufferedImage image) {
        m_bufferedImageCache = image;
    }
    
    /**
     * Force decode and cache the image
     */
    public void loadIntoCache() throws IOException {
        if (m_bufferedImageCache == null) {
            getAsBufferedImage(true);
        }
    }

    // ========== Image Metadata ==========
    
    /**
     * Get image width (decodes if not cached)
     */
    public int getWidth() throws IOException {
        if (m_cachedWidth == null) {
            BufferedImage img = getAsBufferedImage(false);
            if (img != null) {
                m_cachedWidth = img.getWidth();
            }
        }
        return m_cachedWidth != null ? m_cachedWidth : 0;
    }
    
    /**
     * Get image height (decodes if not cached)
     */
    public int getHeight() throws IOException {
        if (m_cachedHeight == null) {
            BufferedImage img = getAsBufferedImage(false);
            if (img != null) {
                m_cachedHeight = img.getHeight();
            }
        }
        return m_cachedHeight != null ? m_cachedHeight : 0;
    }
    
    /**
     * Get cached width without decoding (returns null if not known)
     */
    public Integer getCachedWidth() {
        return m_cachedWidth;
    }
    
    /**
     * Get cached height without decoding (returns null if not known)
     */
    public Integer getCachedHeight() {
        return m_cachedHeight;
    }
    
    /**
     * Get image format (PNG, JPEG, GIF, etc.)
     */
    public String getFormat() {
        if (m_cachedFormat == null) {
            try {
                byte[] bytes = get();
                m_cachedFormat = ImageHelpers.detectImageFormat(bytes);
            } catch (Exception e) {
                m_cachedFormat = "UNKNOWN";
            }
        }
        return m_cachedFormat;
    }
    
    // ========== Cache Management ==========
    
    /**
     * Clear the cached BufferedImage (keeps byte data)
     */
    public void clearCache() {
        m_bufferedImageCache = null;
    }
    
    /**
     * Enable or disable automatic caching
     */
    public void setCacheEnabled(boolean enabled) {
        m_cacheEnabled = enabled;
        if (!enabled) {
            clearCache();
        }
    }
    
    /**
     * Check if caching is enabled
     */
    public boolean isCacheEnabled() {
        return m_cacheEnabled;
    }
    
    /**
     * Get approximate memory usage of cached image
     */
    public long getCacheMemoryUsage() {
        if (m_bufferedImageCache == null) {
            return 0;
        }
        
        int width = m_bufferedImageCache.getWidth();
        int height = m_bufferedImageCache.getHeight();
        int colorModel = m_bufferedImageCache.getColorModel().getPixelSize();
        
        // Rough estimate: width * height * bytes per pixel
        return (long) width * height * (colorModel / 8);
    }

    public NoteBytesImage copy() {
        NoteBytesImage copy = new NoteBytesImage(get());
        copy.m_cacheEnabled = this.m_cacheEnabled;
        copy.m_cachedWidth = this.m_cachedWidth;
        copy.m_cachedHeight = this.m_cachedHeight;
        copy.m_cachedFormat = this.m_cachedFormat;
        // Don't copy the BufferedImage cache (too heavy)
        return copy;
    }

    public void clearBytes(){
        super.clear();
    }
    
    @Override
    public void clear() {
        clearCache();
        m_cachedWidth = null;
        m_cachedHeight = null;
        m_cachedFormat = null;
        super.clear();
    }

    @Override
    public void destroy() {
        clearCache();
        m_cachedWidth = null;
        m_cachedHeight = null;
        m_cachedFormat = null;
        super.destroy();
    }
    
    @Override
    public void ruin() {
        clearCache();
        m_cachedWidth = null;
        m_cachedHeight = null;
        m_cachedFormat = null;
        super.ruin();
    }

    /**
     * Check if byte array appears to be a valid image
     */
    public static boolean isValidImageData(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        
        return !ImageHelpers.detectImageFormat(bytes).equals("UNKNOWN");
    }


  


}
