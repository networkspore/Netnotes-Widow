package io.netnotes.gui.fx.display;

import java.util.ArrayList;
import java.util.List;

public class KeyInterfaceHelpers {

    public static Object getKeyObject(List<? extends Object> items, String key){
        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getKey().equals(key)){
                    return keyItem;
                }
            }
        }
        return null;
    }


    public static void removeOldKeys(List<? extends Object> items, long timeStamp){
        ArrayList<String> keyRemoveList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    keyRemoveList.add(keyItem.getKey());        
                }
            }
        }

        for(String key : keyRemoveList){
            removeKey(items, key);
        }
    }

    public static Object removeKey(List<? extends Object> items, String key){
        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getKey().equals(key)){
                    return items.remove(i);
                }
            }
        }
        return null;
    }
}
