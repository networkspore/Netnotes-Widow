package io.netnotes.gui.fx.components.menus;

import io.netnotes.engine.noteBytes.NoteBytes;

public interface KeyInterface {
    NoteBytes getKey();
    NoteBytes getValue();
    long getTimeStamp();
   
}