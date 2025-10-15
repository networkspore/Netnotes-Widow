package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.control.Button;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.gui.fx.app.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.app.control.layout.LayoutData;

public class TabManagerStage {
    private final Stage stage;
    private final BorderPane root;
    private final TabTopBar topBar;
    private final SideBarPanel sideBar;
    private final StackPane contentArea;
    
    private final HashMap<NoteBytes, ContentTab> tabs;
    private NoteBytes currentTabId;
    
    // Track current content dimensions
    private double contentWidth = 800;
    private double contentHeight = 600;
    
    public TabManagerStage(Stage stage, String title, Image smallIcon15, Image windowIcon100) {
        this.stage = stage;
        this.tabs = new HashMap<>();
        this.currentTabId = null;
        
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
                closeAllTabs();
            } else {
                setCurrentTab(tabId);
            }
        });
        
        // Create sidebar
        sideBar = new SideBarPanel();
        
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
        stage.setScene(scene);
        
        // Register with DeferredLayoutManager
        setupLayoutManagement();
    }
    
    private void setupLayoutManagement() {
        // Register the sidebar for layout
        DeferredLayoutManager.register(stage, sideBar, ctx -> {
            Scene scene = stage.getScene();
            if (scene == null) return new LayoutData.Builder().build();
            
            double sceneHeight = scene.getHeight();
            double topBarHeight = topBar.getHeight();
            
            return new LayoutData.Builder()
                .height(sceneHeight - topBarHeight)
                .build();
        });
        
        // Register the content area for layout
        DeferredLayoutManager.register(stage, contentArea, ctx -> {
            Scene scene = stage.getScene();
            if (scene == null) return new LayoutData.Builder().build();
            
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();
            double sideBarWidth = sideBar.getWidth();
            double topBarHeight = topBar.getHeight();
            
            // Update tracked dimensions
            contentWidth = sceneWidth - sideBarWidth;
            contentHeight = sceneHeight - topBarHeight;
            
            return new LayoutData.Builder()
                .width(contentWidth)
                .height(contentHeight)
                .build();
        });
        
        // Listen for scene size changes
        stage.getScene().widthProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(sideBar);
            DeferredLayoutManager.markDirty(contentArea);
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = tabs.get(currentTabId);
                if (tab != null && tab.getPane() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getPane());
                }
            }
        });
        
        stage.getScene().heightProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(sideBar);
            DeferredLayoutManager.markDirty(contentArea);
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = tabs.get(currentTabId);
                if (tab != null && tab.getPane() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getPane());
                }
            }
        });
        
        // Listen for sidebar expand/collapse
        sideBar.getExpandButton().setOnAction(e -> {
            sideBar.toggleExpanded();
            DeferredLayoutManager.markDirty(contentArea);
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = tabs.get(currentTabId);
                if (tab != null && tab.getPane() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getPane());
                }
            }
        });
    }
    
    public void addTab(NoteBytes id, String title, AppBox appBox) {
        if (tabs.containsKey(id)) {
            // Tab already exists, just switch to it
            setCurrentTab(id);
            return;
        }
        
        ContentTab tab = new ContentTab(id, appBox.getAppId(), title, appBox);
        tabs.put(id, tab);
        
        // Register AppBox with layout manager
        DeferredLayoutManager.register(stage, appBox, ctx -> {
            // Only layout if this is the active tab
            if (currentTabId != null && currentTabId.equals(id)) {
                return new LayoutData.Builder()
                    .width(contentWidth)
                    .height(contentHeight)
                    .build();
            }
            return new LayoutData.Builder().build();
        });
        
        // Setup close handler
        tab.onCloseBtn(e -> removeTab(id));
        
        // Add to top bar
        topBar.addTab(tab);
        
        // Set as current tab
        setCurrentTab(id);
    }
    
    public void removeTab(NoteBytes id) {
        boolean isCurrentTab = currentTabId != null && currentTabId.equals(id);
        
        if (isCurrentTab) {
            contentArea.getChildren().clear();
            currentTabId = null;
        }
        
        ContentTab tab = tabs.remove(id);
        if (tab != null) {
            AppBox appBox = (AppBox) tab.getPane();
            appBox.shutdown();
            topBar.removeTab(id);
        }
        
        // If this was the current tab, switch to another
        if (isCurrentTab && !tabs.isEmpty()) {
            for (NoteBytes tabId : tabs.keySet()) {
                setCurrentTab(tabId);
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
        if (!tabs.containsKey(id)) {
            return;
        }
        
        currentTabId = id;
        contentArea.getChildren().clear();
        
        ContentTab tab = tabs.get(id);
        if (tab != null) {
            contentArea.getChildren().add(tab.getPane());
            topBar.setActiveTab(id);
            
            // Trigger layout for the new active tab
            DeferredLayoutManager.markDirty(tab.getPane());
        }
    }
    
    public NoteBytes getCurrentTabId() {
        return currentTabId;
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
    
    public double getContentWidth() {
        return contentWidth;
    }
    
    public double getContentHeight() {
        return contentHeight;
    }
}