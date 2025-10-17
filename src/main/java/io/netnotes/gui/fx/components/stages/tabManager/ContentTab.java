package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.KeyMenuItem;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;

public class ContentTab {
    private final NoteBytesArray id;
    private final NoteBytes parentId;
    private final String title;
    private final ContentBox appBox;
    private final HBox tabBox;
    private final Button closeBtn;
    private final Label titleLabel;

    private SimpleObjectProperty<NoteBytesArray> m_currentIdProperty = null;
    private ChangeListener<NoteBytesArray> m_currentIdListener;

    private EventHandler<ActionEvent> onTabClickHandler;
    
    private TabWindow parentWindow = null;

    public ContentTab(NoteBytesArray id, NoteBytes parentId, String title, ContentBox content, TabWindow initialWindow) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.appBox = content;
        this.parentWindow = initialWindow;

        // Create tab box for display in horizontal tabs
        tabBox = new HBox(5);
        tabBox.setPadding(new Insets(2,3,2,2));
        tabBox.setId("tabBtn");
        tabBox.setFocusTraversable(true);
        tabBox.setAlignment(Pos.CENTER_LEFT);
        
        titleLabel = new Label(title);
        titleLabel.setId("tabLabel");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPadding(new Insets(0,2,0,5));
        
        closeBtn = new BufferedButton(FxResourceFactory.CLOSE_ICON,  20);
        closeBtn.setId("closeBtn");
        closeBtn.setPadding(new Insets(0, 5, 0, 3));

        HBox tabCloseBox = new HBox();
        tabCloseBox.setMinWidth(FxResourceFactory.MENU_BAR_IMAGE_WIDTH);
        
        tabBox.getChildren().addAll(titleLabel, tabCloseBox);
        
        // Update style when this tab is active
        m_currentIdListener = (obs, old, newVal) -> 
            updateCurrentId(newVal != null && newVal.equals(id));
        
        tabBox.setOnMouseClicked(e -> {
            if (onTabClickHandler != null) {
                onTabClickHandler.handle(new ActionEvent());
            }
        });

        tabBox.onMouseEnteredProperty().addListener((mouseEvent)->{
            if(!tabCloseBox.getChildren().contains(closeBtn)){
                tabCloseBox.getChildren().add(closeBtn);
            }
        }); 

        tabBox.onMouseExitedProperty().addListener(mouseEvent->{
            if(tabCloseBox.getChildren().contains(closeBtn)){
                tabCloseBox.getChildren().remove(closeBtn);
            }
        });

       
    }

    public void setCurrentIdProperty(SimpleObjectProperty<NoteBytesArray> currentIdProperty){
        if(m_currentIdProperty != null){
            m_currentIdProperty.removeListener(m_currentIdListener);
        }
   
        m_currentIdProperty = currentIdProperty;
         updateCurrentId();

        if(m_currentIdProperty != null){
            m_currentIdProperty.addListener(m_currentIdListener);
        }
       
    }

    public void updateCurrentId(){
        updateCurrentId(m_currentIdProperty != null && m_currentIdProperty.get() != null ? m_currentIdProperty.get().equals(id) :false);
    }

    public void updateCurrentId(boolean current){
        titleLabel.setId(current ? "tabLabelSelected" : "tabLabel");
        tabBox.setId(current ? "tabBtnSelected" : "tabBtn");
    }

     // Location management
    public TabWindow getParentWindow() {
        return parentWindow;
    }
    
    public void setParentWindow(TabWindow window) {
        this.parentWindow = window;
    }
    
    public boolean isInPrimaryWindow() {
        return parentWindow != null && parentWindow.isPrimaryWindow();
    }
    
    public Stage getStage() {
        return parentWindow != null ? parentWindow.getStage() : null;
    }

    public KeyMenuItem getMenuItem() {
        KeyMenuItem menuItem = new KeyMenuItem(id, new NoteString(title), System.currentTimeMillis(), KeyMenuItem.VALUE_NOT_KEY);
        menuItem.setId("tabMenuItem");
        Runnable updateTabCss = ()->{
            NoteBytesArray currentId = getParentWindow().currentIdProperty().get();
            if(currentId != null && currentId.equals(getId())){
                menuItem.setId("selectedMenuItem");
            }else{
                menuItem.setId("tabMenuItem");
            }
        };

        getParentWindow().currentIdProperty().addListener((obs,oldVal,newVal)->updateTabCss.run());
        updateTabCss.run();
        return menuItem;
    }
    public NoteBytesArray getId() { return id; }
    public NoteBytes getParentId() { return parentId; }
    public String getTitle() { return title; }
    public ContentBox getAppBox() { return appBox; }
    public HBox getTabBox() { return tabBox; }
    
    public void onCloseBtn(EventHandler<ActionEvent> handler) {
        closeBtn.setOnAction(handler);
    }
    
    public void onTabClicked(EventHandler<ActionEvent> handler) {
        this.onTabClickHandler = handler;
    }
    
    
    public void shutdown() {
        // Override in subclasses to cleanup
    }
}