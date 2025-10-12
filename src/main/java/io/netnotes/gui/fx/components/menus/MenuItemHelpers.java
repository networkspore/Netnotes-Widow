package io.netnotes.gui.fx.components.menus;

import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;

public class MenuItemHelpers {
    public static int findMenuItemIndex(ObservableList<MenuItem> list, String id){
        if(id != null){
            for(int i = 0; i < list.size() ; i++){
                MenuItem menuItem = list.get(i);
                Object userData = menuItem.getUserData();

                if(userData != null && userData instanceof String){
                    String menuItemId = (String) userData;
                    if(menuItemId.equals(id)){
                        return i;
                    }
                }
            }
        }

        return -1;
    }
}
