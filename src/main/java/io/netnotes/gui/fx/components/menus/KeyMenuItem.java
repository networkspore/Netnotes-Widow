package io.netnotes.gui.fx.components.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.NoteBytes;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;

public class KeyMenuItem extends MenuItem implements KeyInterface{
    public static final String KEY_AND_VALUE = "keyAndValue";
    public static final String KEY_NOT_VALUE = "keyNotValue";
    public static final String NOT_KEY_VALUE = "notKeyValue";
    public static final String VALUE_AND_KEY = "valueAndKey";
    public static final String VALUE_NOT_KEY = "valueNotKey";

    public static final int DEFAULT_COL_SIZE = 20;
    private int m_colSize =DEFAULT_COL_SIZE;
    private long m_timeStamp = 0;
    private NoteBytes m_key = null;
    private NoteBytes m_value = null;

    private String m_style = KEY_AND_VALUE;

    public KeyMenuItem(NoteBytes key, NoteBytes value, long timeStamp, String style){
        this(key, value, timeStamp, DEFAULT_COL_SIZE, style);
    }

    public KeyMenuItem(NoteBytes key, NoteBytes value, long timeStamp, int colSize, String style){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        m_style = style;
        update();
    }

    public KeyMenuItem(NoteBytes key, NoteBytes value, long timeStamp){
        this(key, value, timeStamp, DEFAULT_COL_SIZE);
    }
    public KeyMenuItem(NoteBytes key, NoteBytes value, long timeStamp, int colSize){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public void update(){
        m_style = m_style == null ? KEY_AND_VALUE : m_style;

        switch(m_style){
            case KEY_AND_VALUE:
                setText( String.format("%-"+m_colSize +"s", m_key + ": ") + String.format("%"+m_colSize +"s", m_value));
            break;
            case KEY_NOT_VALUE:
                setText( String.format("%-"+m_colSize +"s", m_key));
            break;
            case NOT_KEY_VALUE:
                setText(String.format("%"+m_colSize +"s", m_value));
            break;
            case VALUE_AND_KEY:
                setText(String.format("%"+m_colSize +"s", m_value + ": ") + String.format("%-"+m_colSize +"s", m_key));
            break;
            case VALUE_NOT_KEY:
                setText(String.format("%"+m_colSize +"s", m_value));
            break;   
        }
    }

    public long getTimeStamp(){
        return m_timeStamp;
    }

    public void setTimeStamp(long timeStamp){
        m_timeStamp = timeStamp;;
    }

    public void setValue(NoteBytes value, long timeStamp){
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public NoteBytes getKey(){
        return m_key;
    }

    public NoteBytes getValue(){
        return m_value;
    }

    public static KeyMenuItem getKeyMenuItem(List<MenuItem> items, NoteBytes key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getKey().equals(key)){
                    return keyItem;
                }
            }
        }
        return null;
    }

    public static void removeeOldKeyItems(List<MenuItem> items, long timeStamp){
         ArrayList<NoteBytes> removeList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    removeList.add(keyItem.getKey());        
                }
            }
        }

        for(NoteBytes key : removeList){
            removeKeyItem(items, key);
        }
    }

    public static KeyMenuItem removeKeyItem(List<MenuItem> items, NoteBytes key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getKey().equals(key)){
                    return (KeyMenuItem) items.remove(i);
                }
            }
        }
        return null;
    }

    public static void updateMenu(Menu menu, JsonObject json){
        long timeStamp = System.currentTimeMillis();
            if(menu.getItems().size() == 0){
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    KeyMenuItem item = new KeyMenuItem(new NoteBytes(entry.getKey()),new NoteBytes( entry.getValue().toString()), timeStamp);
                    menu.getItems().add(item);
                }
            }else{
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    NoteBytes key =  new NoteBytes( entry.getKey());
                    NoteBytes value = new NoteBytes(entry.getValue().toString());

                    KeyMenuItem existingItem =  KeyMenuItem.getKeyMenuItem(menu.getItems(), key);
                    
                    if(existingItem != null){
                        existingItem.setValue(value, timeStamp);
                    }else{
                        KeyMenuItem item = new KeyMenuItem(key, value, timeStamp);
                        menu.getItems().add(item);
                    }
                }

                KeyMenuItem.removeeOldKeyItems(menu.getItems(), timeStamp);

            }
    }
}
