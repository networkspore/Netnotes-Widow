package io.netnotes.gui.fx.noteBytes;

import java.io.IOException;

import javafx.scene.image.Image;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.gui.fx.display.ImageHelpers;

public class NoteBytesImage extends NoteBytes {

    public NoteBytesImage(byte[] bytes) {
        super(bytes, NoteBytesMetaData.IMAGE_TYPE);
    }

    public static NoteBytesImage create(Image image) throws IOException{
        return new NoteBytesImage(ImageHelpers.getImageAsEncodedBytes(image, NoteMessaging.ImageEncoding.PNG));
    }

    public static NoteBytesImage create(Image image, String encoding) throws IOException{
        return new NoteBytesImage(ImageHelpers.getImageAsEncodedBytes(image, encoding));
    }
}
