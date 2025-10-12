package io.netnotes.gui.fx.components.menus;


import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.images.BufferedImageView;
import javafx.scene.control.MenuButton;
import javafx.scene.image.Image;

public class BufferedMenuButton extends MenuButton {

    private BufferedImageView m_imgBufView;
    private boolean m_isPressedEffects = true;

    public BufferedMenuButton() {
        this(FxResourceFactory.MENU_ICON);
    }

    public BufferedMenuButton(String urlString) {
        this("", urlString, 30);
    }

    public BufferedMenuButton(String name, String urlString) {
        this(name, urlString, 30);
    }

    public BufferedMenuButton(String urlString, double imageWidth) {
        super();
        m_imgBufView = new BufferedImageView(new Image(urlString), imageWidth);
        setGraphic(m_imgBufView);

        enablePressedEffects();
    }

    public BufferedMenuButton(String text, String urlString, double imageWidth) {
        super(text);
        m_imgBufView = new BufferedImageView(new Image(urlString), imageWidth);
        setGraphic(m_imgBufView);


        enablePressedEffects();
    }

    public void disablePressedEffects() {
        setOnMousePressed(null);
        setOnMouseReleased(null);
        m_imgBufView.removeEffect(BufferedButton.ON_MOUSE_PRESSED_EFFECT_ID); 
        m_isPressedEffects = false;
    }
    
    public void enablePressedEffects(){
        setOnMousePressed((event) -> {
            m_imgBufView.applyBrightnessEffect(BufferedButton.ON_MOUSE_PRESSED_EFFECT_ID, BufferedButton.DEFAULT_DARKEN_AMOUNT);
            show();
        });
        setOnMouseReleased((event) -> {
            m_imgBufView.removeEffect(BufferedButton.ON_MOUSE_PRESSED_EFFECT_ID);
        });
        m_isPressedEffects = true;
    }

    

    public BufferedImageView getBufferedImageView() {
        return m_imgBufView;
    }

    public void setImage(Image image) {

        m_imgBufView.setDefaultImage(image);

    }

    public boolean isPressedEffects(){
        return m_isPressedEffects;
    }
}
