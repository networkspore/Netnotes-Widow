package io.netnotes.gui.fx.components.images.effects;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.utils.MathHelpers;
import io.netnotes.gui.fx.components.images.scaling.ZoomRGB;
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
                ZoomRGB.zoomRGB(img, m_algorithm, m_amount, MathHelpers.clampDimension(m_centerX, img.getWidth()), MathHelpers.clampDimension(m_centerY, img.getHeight()));
            }else{
                ZoomRGB.zoomRGB(img, m_algorithm, m_amount);
            }
        }else{
            ZoomRGB.zoomRGB(img,m_algorithm, m_crop);
        }
    }

    
}
