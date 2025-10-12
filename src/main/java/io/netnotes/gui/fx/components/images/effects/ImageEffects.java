package io.netnotes.gui.fx.components.images.effects;

import java.awt.image.BufferedImage;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteUUID;

public class ImageEffects {

    private String m_name;
    private NoteBytes m_id;

    public ImageEffects(String name) {
        m_id = NoteUUID.createLocalUUID128();
        m_name = name;
    }

    public ImageEffects(NoteBytes id, String name) {
        m_id = id;
        m_name = name;
    }

    public void applyEffect(BufferedImage img) {

    }

    public String getName() {
        return m_name;
    }

    public NoteBytes getId() {
        return m_id;
    }
}
