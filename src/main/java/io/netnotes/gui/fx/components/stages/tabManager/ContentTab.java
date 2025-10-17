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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class ContentTab {
    private final NoteBytesArray id;
    private final NoteBytes parentId;
    private final String title;
    private final AppBox appBox;
    private final HBox tabBox;
    private final Button closeBtn;

    private SimpleObjectProperty<NoteBytes> currentId;
    private EventHandler<ActionEvent> onTabClickHandler;
    private Stage tabStage = null; 

    public ContentTab(NoteBytesArray id, NoteBytes parentId, String title, AppBox pane, Stage tabStage) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.appBox = pane;
        this.currentId = new SimpleObjectProperty<>();
        this.tabStage = tabStage;

        // Create tab box for display in horizontal tabs
        tabBox = new HBox(5);
        tabBox.setPadding(new Insets(2,3,2,2));
        tabBox.setId("tabBtn");
        tabBox.setFocusTraversable(true);
        tabBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
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
        currentId.addListener((obs, old, newVal) -> {
            boolean current = newVal != null && newVal.equals(id);

            titleLabel.setId(current ? "tabLabelSelected" : "tabLabel");
            tabBox.setId(current ? "tabBtnSelected" : "tabBtn");
        });
        
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

    public Stage getStage(){
        return tabStage;
    }


    public KeyMenuItem getMenuItem() {
        KeyMenuItem menuItem = new KeyMenuItem(id, new NoteString(title), System.currentTimeMillis(), KeyMenuItem.VALUE_NOT_KEY);
        menuItem.setStyle("-fx-padding: 5px 10px;");
        return menuItem;
    }
    public NoteBytesArray getId() { return id; }
    public NoteBytes getParentId() { return parentId; }
    public String getTitle() { return title; }
    public AppBox getAppBox() { return appBox; }
    public HBox getTabBox() { return tabBox; }
    
    public void onCloseBtn(EventHandler<ActionEvent> handler) {
        closeBtn.setOnAction(handler);
    }
    
    public void onTabClicked(EventHandler<ActionEvent> handler) {
        this.onTabClickHandler = handler;
    }
    
    public ReadOnlyObjectProperty<NoteBytes> currentIdProperty() {
        return currentId;
    }
    
    public void shutdown() {
        // Override in subclasses to cleanup
    }
}