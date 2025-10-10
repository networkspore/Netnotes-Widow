package io.netnotes.gui.fx.noteBytes;

import java.io.IOException;

import javafx.scene.image.Image;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.gui.fx.display.ImageHelpers;
import io.netnotes.gui.fx.display.ImageHelpers.ImageEncoding;

public class NoteBytesImage extends NoteBytes {

    public NoteBytesImage(byte[] bytes) {
        super(bytes, NoteBytesMetaData.IMAGE_TYPE);
    }

    public static NoteBytesImage of(Image image) throws IOException{
        return new NoteBytesImage(ImageHelpers.getImageAsEncodedBytes(image, ImageEncoding.PNG));
    }

    public static NoteBytesImage of(Image image, String encoding) throws IOException{
        return new NoteBytesImage(ImageHelpers.getImageAsEncodedBytes(image, encoding));
    }
}
