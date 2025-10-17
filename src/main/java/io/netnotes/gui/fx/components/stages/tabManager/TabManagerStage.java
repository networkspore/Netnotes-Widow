package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;

public class TabManagerStage {
    private final static double DEFAULT_WIDTH = 1000;
    private final static double DEFAULT_HEIGHT = 650;

    private final Stage stage;
    private final Scene scene;
    private final BorderPane root;
    private final TabTopBar topBar;
    private final SideBarPanel sideBar;
    private final StackPane contentArea;
    
    private NoteBytes currentTabId;
    
    // Track current content dimensions
    private double contentWidth = 800;
    private double contentHeight = 600;

    private final Runnable m_onClose;
    private final ConcurrentHashMap<NoteBytesArray, ContentTab> allTabs = new ConcurrentHashMap<>();
    
    public TabManagerStage(Stage stage, String title, Image smallIcon15, Image windowIcon100, Runnable onClose) {
        this.stage = stage;
        this.currentTabId = null;
        // Setup stage
        stage.setTitle(title);
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.getIcons().add(windowIcon100);
        m_onClose = onClose;
        // Create close button
        BufferedButton closeBtn = new BufferedButton(FxResourceFactory.closeImg,20);
        closeBtn.setOnAction(e -> m_onClose.run());
        
        // Create top bar with tab management
        topBar = new TabTopBar(smallIcon15, title, closeBtn, stage, allTabs);
        topBar.setTabSelectionListener(tabId -> {
            if (tabId == null) {
                closeAllTabs();
            } else {
                setCurrentTab(new NoteBytesArray(tabId.get()));
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
        this.scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.scene.setFill(null);
        stage.setScene(scene);
        this.scene.getStylesheets().add(FxResourceFactory.DEFAULT_CSS);
        
        // Register with DeferredLayoutManager
        setupLayoutManagement();
    }

    public Scene getScene(){
        return scene;
    }
    
    private void setupLayoutManagement() {
        // Register the sidebar for layout
        DeferredLayoutManager.register(stage, sideBar, ctx -> {
            if (scene == null) return new LayoutData.Builder().build();
            
            double sceneHeight = scene.getHeight();
            double topBarHeight = topBar.getHeight();
            
            return new LayoutData.Builder()
                .height(sceneHeight - topBarHeight)
                .build();
        });
        
        // Register the content area for layout
        DeferredLayoutManager.register(stage, contentArea, ctx -> {

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
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
        
        stage.getScene().heightProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(sideBar);
            DeferredLayoutManager.markDirty(contentArea);
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
        
        // Listen for sidebar expand/collapse
        sideBar.getExpandButton().setOnAction(e -> {
            sideBar.toggleExpanded();
            DeferredLayoutManager.markDirty(contentArea);
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof AppBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
    }
    
    public void addTab(NoteBytes tabId, NoteBytes parentId, String title, AppBox appBox) {
        NoteBytesArray compositeId = new NoteBytesArray(parentId, tabId);

        if (this.allTabs.containsKey(compositeId)) {
            // Tab already exists, just switch to it
            setCurrentTab(compositeId);
            return;
        }
        
        ContentTab tab = new ContentTab(compositeId, parentId, title, appBox, getStage());
       
        
        // Register AppBox with layout manager
        DeferredLayoutManager.register(stage, appBox, ctx -> {
            // Only layout if this is the active tab
            if (currentTabId != null && currentTabId.equals(compositeId)) {
                return new LayoutData.Builder()
                    .width(contentWidth)
                    .height(contentHeight)
                    .build();
            }
            return new LayoutData.Builder().build();
        });
        
        // Setup close handler
        tab.onCloseBtn(e -> removeTab(compositeId));
        
        // Add to top bar
        topBar.addTab(tab);
        
        // Set as current tab
        setCurrentTab(compositeId);
    }
    
    public void removeTab(NoteBytes id, NoteBytes parentId) {
        removeTab(new NoteBytesArray(id, parentId));
    }

    protected void removeTab(NoteBytesArray id) {
       
        boolean isCurrentTab = currentTabId != null && currentTabId.equals(id);
        
        if (isCurrentTab) {
            contentArea.getChildren().clear();
            currentTabId = null;
        }
        
        ContentTab tab = topBar.removeTab(id);
        if (tab != null) {
            AppBox appBox = (AppBox) tab.getAppBox();
            appBox.shutdown();
            topBar.removeTab(id);
        }
        
        // If this was the current tab, switch to another
        if (isCurrentTab && !this.allTabs.isEmpty()) {
            for (NoteBytesArray tabId : this.allTabs.keySet()) {
                setCurrentTab(tabId);
                break;
            }
        }
    }
    
    public void removeTabsByParentId(NoteBytes parentId) {
        ArrayList<NoteBytesArray> toRemove = new ArrayList<>();
        for (Map.Entry<NoteBytesArray, ContentTab> entry : this.allTabs.entrySet()) {
            ContentTab tab = entry.getValue();
            if (parentId != null && parentId.equals(tab.getParentId())) {
                toRemove.add(entry.getKey());
            } else if (parentId == null && tab.getParentId() == null) {
                toRemove.add(entry.getKey());
            }
        }
        for (NoteBytesArray id : toRemove) {
            removeTab(id);
        }
    }


     public AppBox[] getAppBoxesByParentId(NoteBytes parentId) {
        ArrayList<AppBox> appBoxes = new ArrayList<>();
        for (Map.Entry<NoteBytesArray, ContentTab> entry : this.allTabs.entrySet()) {
            ContentTab tab = entry.getValue();
            if (parentId != null && parentId.equals(tab.getParentId())) {
                appBoxes.add((AppBox)entry.getValue().getAppBox());
            } 
        }
        return appBoxes.toArray(new AppBox[0]);
    }
    
    public void closeAllTabs() {
        ArrayList<NoteBytesArray> tabIds = new ArrayList<>(this.allTabs.keySet());
        for (NoteBytesArray id : tabIds) {
            removeTab(id);
        }
    }
    
    protected ContentTab getTab(NoteBytesArray id) {
        return this.allTabs.get(id);
    }

    public ContentTab getTab(NoteBytes id, NoteBytes parentId) {
        return getTab(new NoteBytesArray(id, parentId));
    }
    
    protected boolean containsTab(NoteBytesArray id) {
        return this.allTabs.containsKey(id);
    }

    public boolean containsTab(NoteBytes id, NoteBytes parentId) {
        return containsTab(new NoteBytesArray(id, parentId));
    }

    public void setCurrentTab(NoteBytes id, NoteBytes parentId) {
        setCurrentTab(new NoteBytesArray(id, parentId));
    }
    
    protected void setCurrentTab(NoteBytesArray id) {
        if (!this.allTabs.containsKey(id)) {
            return;
        }
        
        currentTabId = id;
        contentArea.getChildren().clear();
        
        ContentTab tab = this.allTabs.get(id);
        if (tab != null) {
            contentArea.getChildren().add(tab.getAppBox());
            topBar.setActiveTab(id);
            
            // Trigger layout for the new active tab
            DeferredLayoutManager.markDirty(tab.getAppBox());
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