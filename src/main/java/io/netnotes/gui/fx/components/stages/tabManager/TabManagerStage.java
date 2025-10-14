package io.netnotes.gui.fx.components.stages.tabManager;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.control.Button;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.components.vboxes.AppBox;

import java.util.ArrayList;

public class TabManagerStage {
    private final Stage stage;
    private final BorderPane root;
    private final TabTopBar topBar;
    private final SideBarPanel sideBar;
    private final StackPane contentArea;
    
    private final HashMap<NoteBytes, ContentTab> tabs;
    private final SimpleObjectProperty<NoteBytes> currentTabId;
    
    private final DoubleProperty contentWidth;
    private final DoubleProperty contentHeight;
    
    public TabManagerStage(Stage stage, String title, Image smallIcon15, Image windowIcon100) {
        this.stage = stage;
        this.tabs = new HashMap<>();
        this.currentTabId = new SimpleObjectProperty<>(null);
        
        // Initialize shared size properties
        this.contentWidth = new SimpleDoubleProperty(800);
        this.contentHeight = new SimpleDoubleProperty(600);
        
        // Setup stage
        stage.setTitle(title);
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.getIcons().add(windowIcon100);
        
        // Create close button
        Button closeBtn = new Button("×");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                         "-fx-font-size: 20px; -fx-padding: 0px 15px;");
        closeBtn.setOnAction(e -> stage.close());
        
        // Create top bar with tab management
        topBar = new TabTopBar(smallIcon15, title, closeBtn, stage);
        topBar.setTabSelectionListener(tabId -> {
            if (tabId == null) {
                // Close all tabs
                closeAllTabs();
            } else {
                currentTabId.set(tabId);
            }
        });
        
        // Create sidebar
        sideBar = new SideBarPanel(contentWidth, contentHeight);
        
        // Create content area
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #1e1e1e;");
        
        // Main layout
        root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(sideBar);
        root.setCenter(contentArea);
        
        // Create scene
        Scene scene = new Scene(root, 1000, 650);
        
        // Bind content dimensions
        contentWidth.bind(scene.widthProperty().subtract(sideBar.widthProperty()));
        contentHeight.bind(scene.heightProperty().subtract(topBar.heightProperty()));
        
        // Listen for tab changes
        currentTabId.addListener((obs, oldVal, newVal) -> {
            contentArea.getChildren().clear();
            if (newVal != null) {
                ContentTab tab = tabs.get(newVal);
                if (tab != null) {
                    contentArea.getChildren().add(tab.getPane());
                    topBar.setActiveTab(newVal);
                } else {
                    currentTabId.set(null);
                }
            }
        });
        
        stage.setScene(scene);
    }
    

    
    public void addTab(NoteBytes id, String title, AppBox appBox) {
        if (tabs.containsKey(id)) {
            // Tab already exists, just switch to it
            currentTabId.set(id);
            return;
        }
        
        ContentTab tab = new ContentTab(id, appBox.getAppId(), title, appBox);
        tabs.put(id, tab);
        
        // Bind app size
        appBox.prefWidthProperty().bind(contentWidth);
        appBox.prefHeightProperty().bind(contentHeight);
        
        // Setup close handler
        tab.onCloseBtn(e -> removeTab(id));
        
        // Add to top bar
        topBar.addTab(tab);
        
        // Set as current tab
        currentTabId.set(id);
    }
    
    public void removeTab(NoteBytes id) {
        boolean isCurrentTab = currentTabId.get() != null && currentTabId.get().equals(id);
        
        if (isCurrentTab) {
            currentTabId.set(null);
        }
        
        ContentTab tab = tabs.remove(id);
        if (tab != null) {
            AppBox appBox = (AppBox) tab.getPane();
            appBox.prefWidthProperty().unbind();
            appBox.prefHeightProperty().unbind();
            appBox.shutdown();
            
            topBar.removeTab(id);
        }
        
        // If this was the current tab, switch to another
        if (isCurrentTab && !tabs.isEmpty()) {
            for (NoteBytes tabId : tabs.keySet()) {
                currentTabId.set(tabId);
                break;
            }
        }
    }
    
    public void removeTabsByParentId(NoteBytes parentId) {
        ArrayList<NoteBytes> toRemove = new ArrayList<>();
        for (Map.Entry<NoteBytes, ContentTab> entry : tabs.entrySet()) {
            ContentTab tab = entry.getValue();
            if (parentId != null && parentId.equals(tab.getParentId())) {
                toRemove.add(entry.getKey());
            } else if (parentId == null && tab.getParentId() == null) {
                toRemove.add(entry.getKey());
            }
        }
        for (NoteBytes id : toRemove) {
            removeTab(id);
        }
    }
    
    public void closeAllTabs() {
        ArrayList<NoteBytes> tabIds = new ArrayList<>(tabs.keySet());
        for (NoteBytes id : tabIds) {
            removeTab(id);
        }
    }
    
    public ContentTab getTab(NoteBytes id) {
        return tabs.get(id);
    }
    
    public boolean containsTab(NoteBytes id) {
        return tabs.containsKey(id);
    }
    
    public void setCurrentTab(NoteBytes id) {
        if (tabs.containsKey(id)) {
            currentTabId.set(id);
        }
    }
    
    public NoteBytes getCurrentTabId() {
        return currentTabId.get();
    }
    
    public SideBarPanel getSideBar() {
        return sideBar;
    }
    
    public TabTopBar getTopBar() {
        return topBar;
    }
    
    public void show() {
        stage.show();
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public DoubleProperty contentWidthProperty() {
        return contentWidth;
    }
    
    public DoubleProperty contentHeightProperty() {
        return contentHeight;
    }
}