package io.netnotes.gui.fx.components.buttons;




import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.components.images.BufferedImageView;
import javafx.scene.control.Button;
import javafx.scene.image.Image;

public class BufferedButton extends Button {

    public final static NoteBytesReadOnly ON_MOUSE_PRESSED_EFFECT_ID = new NoteBytesReadOnly("onMousePressed");
    public final static double DEFAULT_DARKEN_AMOUNT = -.6;
    private BufferedImageView m_imgBufView;
    private boolean m_isPressedEffects = true;

    public BufferedButton() {
        this(FxResourceFactory.MENU_ICON);
    }

    public BufferedButton(String urlString) {
        this(new Image(urlString));
    }

      public BufferedButton(Image image, double imageWidth){
        this(image);
        m_imgBufView.setFitWidth(imageWidth);
        m_imgBufView.setPreserveRatio(true);
    }


    public BufferedButton(Image image) {
        super();
        m_imgBufView = new BufferedImageView(image);
        m_imgBufView.setPreserveRatio(true);
        setGraphic(m_imgBufView);
        init();
    }

    public BufferedButton(String urlString, double imageWidth) {
        super();
        
        m_imgBufView = urlString != null 
            ? new BufferedImageView(new Image(urlString), imageWidth)
            : new BufferedImageView(imageWidth);

        setGraphic(m_imgBufView);
        init();
    }

    public BufferedButton(String text, String urlString, double imageWidth) {
        super(text);
        if(urlString != null){
            m_imgBufView = new BufferedImageView(new Image(urlString), imageWidth);
        }else{
            m_imgBufView = new BufferedImageView();
        }
        setGraphic(m_imgBufView);

        init();
    }

    private void init(){
        setId("menuBtn");
        enablePressedEffects();
    }

    public BufferedImageView getBufferedImageView() {
        return m_imgBufView;
    }

    public void setImage(Image image) {

        m_imgBufView.setDefaultImage(image);

    }


    public void disablePressedEffects() {
        setOnMousePressed(null);
        setOnMouseReleased(null);
        m_imgBufView.removeEffect(ON_MOUSE_PRESSED_EFFECT_ID); 
        m_isPressedEffects = false;
    }
    
    public void enablePressedEffects(){
        setOnMousePressed((pressedEvent) -> m_imgBufView.applyBrightnessEffect(ON_MOUSE_PRESSED_EFFECT_ID, DEFAULT_DARKEN_AMOUNT));
        setOnMouseReleased((pressedEvent) -> m_imgBufView.removeEffect(ON_MOUSE_PRESSED_EFFECT_ID));
        m_isPressedEffects = true;
    }

    public boolean isPressedEffects(){
        return m_isPressedEffects;
    }
}
