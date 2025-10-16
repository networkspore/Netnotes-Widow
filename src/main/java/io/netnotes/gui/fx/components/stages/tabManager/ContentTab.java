package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Button;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;



import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class ContentTab {
    private final NoteBytesArray id;
    private final NoteBytes parentId;
    private final String title;
    private final AppBox appBox;
    private final HBox tabBox;
    private final Button closeBtn;
    private final MenuItem menuItem;
    private SimpleObjectProperty<NoteBytes> currentId;
    private EventHandler<ActionEvent> onTabClickHandler;
    

    public ContentTab(NoteBytesArray id, NoteBytes parentId, String title, AppBox pane) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.appBox = pane;
        this.currentId = new SimpleObjectProperty<>();
        
        // Create tab box for display in horizontal tabs
        tabBox = new HBox(5);
        tabBox.setPadding(new Insets(5, 10, 5, 10));
        tabBox.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5 5 0 0;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #cccccc;");
        
        closeBtn = new Button("×");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; " +
                         "-fx-font-size: 14px; -fx-padding: 0 5 0 5;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #ff4444; " +
            "-fx-font-size: 14px; -fx-padding: 0 5 0 5;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #888888; " +
            "-fx-font-size: 14px; -fx-padding: 0 5 0 5;"));
        
        tabBox.getChildren().addAll(titleLabel, closeBtn);
        
        // Update style when this tab is active
        currentId.addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.equals(id)) {
                tabBox.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 5 5 0 0;");
                titleLabel.setStyle("-fx-text-fill: #ffffff;");
            } else {
                tabBox.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 5 5 0 0;");
                titleLabel.setStyle("-fx-text-fill: #cccccc;");
            }
        });
        
        tabBox.setOnMouseClicked(e -> {
            if (onTabClickHandler != null) {
                onTabClickHandler.handle(new ActionEvent());
            }
        });

        menuItem = new MenuItem(title);
        menuItem.setStyle("-fx-padding: 5px 10px;");
    }
    
    public MenuItem getMenuItem() {return menuItem;}
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