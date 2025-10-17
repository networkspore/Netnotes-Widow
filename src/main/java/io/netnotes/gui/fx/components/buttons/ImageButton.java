package io.netnotes.gui.fx.components.buttons;

import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

public class ImageButton extends Button {
    private Image m_image;
    private ImageView m_imageView;

    public ImageButton(Image image, String name){
        super(name);
        m_image = image;
    
        m_imageView = new ImageView(image);
        m_imageView.setFitHeight(100);
        m_imageView.setPreserveRatio(true);

        this.setGraphic(m_imageView);
        this.setId("startImageBtn");
        this.setFont(FxResourceFactory.mainFont);
        this.setContentDisplay(ContentDisplay.TOP);
    }

    public ImageView getImageView(){
        return m_imageView;
    }

    public void setSize(double size){
        m_imageView.setFitHeight(size);
    }

 
    public void setImage(Image image){
        m_image = image;
        m_imageView.setImage(image);
    }

    public Image getImage(){
        return m_image;
    }
}
