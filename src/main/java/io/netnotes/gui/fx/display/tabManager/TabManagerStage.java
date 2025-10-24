package io.netnotes.gui.fx.display.tabManager;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.image.Image;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.StageLayout;

public class TabManagerStage implements TabWindow {
    private final static double DEFAULT_WIDTH = 1000;
    private final static double DEFAULT_HEIGHT = 650;


    private Stage stage;
    private Scene scene;
    private BorderPane root;
    private TabTopBar topBar;
    private SideBarPanel sideBar;
    private StackPane contentArea;
    
    private boolean m_started = false;
    private String m_title;
    private Image m_smallIcon15;
    private Image m_appIcon100;

    private SimpleObjectProperty<NoteBytesArray> m_currentTabIdProperty = new SimpleObjectProperty<>(null);
    
    private double contentWidth = 800;
    private double contentHeight = 600;

    private final Runnable m_onClose;
    private final ConcurrentHashMap<NoteBytesArray, ContentTab> allTabs = new ConcurrentHashMap<>();
    
    
    public TabManagerStage(Stage stage, String title, Image smallIcon15, Image windowIcon100, Runnable onClose) {
        this.stage = stage;
        this.stage.setTitle(title);
        this.m_title = title;
        m_smallIcon15 = smallIcon15;
        m_appIcon100 = windowIcon100;
        m_onClose = onClose;
    }

    public void start(){
        if(!m_started){
            m_started = true;
    
            DeferredLayoutManager.registerStage(stage, _ -> {
                // Callback for stage-level positioning
                // For main window, we might not need to reposition
                return new StageLayout.Builder()
                    .x(stage.getX())
                    .y(stage.getY())
                    .width(stage.getWidth())
                    .height(stage.getHeight())
                    .build();
            });

            
            // Create close button
            BufferedButton closeBtn = new BufferedButton(FxResourceFactory.closeImg,20);
            closeBtn.setOnAction(_ -> m_onClose.run());
            
            // Create top bar with tab management
            topBar = new TabTopBar(m_smallIcon15, m_title, closeBtn, stage, this);

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

            m_currentTabIdProperty.addListener((_,_, newval)->{
                if (newval == null){
                    contentArea.getChildren().clear();
                    return;
                }

                ContentTab tab = getTab(newval);

                if (tab == null || tab.getParentWindow() != this) return;
            
                contentArea.getChildren().clear();
                contentArea.getChildren().add(tab.getAppBox());
                DeferredLayoutManager.markDirty(tab.getAppBox());
            });

            getSideBar().initializeLayout(stage);
        }
    }

    public Scene getScene(){
        return scene;
    }

    public String getTitle(){
        return m_title;
    }

    public void setTitle(String title){
        m_title = title;
        stage.setTitle(m_title);
    }

    public Image getSmallIcon(){
        return m_smallIcon15;
    }

    public Image getAppIcon(){
        return m_appIcon100;
    }
    
    private void setupLayoutManagement() {
        // Register the sidebar for layout
        DeferredLayoutManager.register(stage, sideBar, _ -> {
            if (scene == null) return new LayoutData.Builder().build();
            
            double sceneHeight = scene.getHeight();
            double topBarHeight = topBar.getHeight();
            
            return new LayoutData.Builder()
                .height(sceneHeight - topBarHeight)
                .build();
        });
        
        // Register the content area for layout
        DeferredLayoutManager.register(stage, contentArea, _ -> {

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
        stage.getScene().widthProperty().addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(sideBar);
            DeferredLayoutManager.markDirty(contentArea);
            NoteBytesArray currentTabId = m_currentTabIdProperty.get();
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof ContentBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
        
        stage.getScene().heightProperty().addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(sideBar);
            DeferredLayoutManager.markDirty(contentArea);
            NoteBytesArray currentTabId = m_currentTabIdProperty.get();
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof ContentBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
        
        // Listen for sidebar expand/collapse
        sideBar.getM_expandButton().setOnAction(_ -> {
            sideBar.toggleExpanded();
            DeferredLayoutManager.markDirty(contentArea);
            NoteBytesArray currentTabId = m_currentTabIdProperty.get();
            // Trigger layout for all active tabs
            if (currentTabId != null) {
                ContentTab tab = this.allTabs.get(currentTabId);
                if (tab != null && tab.getAppBox() instanceof ContentBox) {
                    DeferredLayoutManager.markDirty(tab.getAppBox());
                }
            }
        });
    }
    
     /**
     * Create a new tab and add to tracking
     */
    public void addTab(NoteBytes tabId, NoteBytes parentId, String title, ContentBox appBox) {
        NoteBytesArray compositeId = new NoteBytesArray(parentId, tabId);

        if (this.allTabs.containsKey(compositeId)) {
            setCurrentTab(compositeId);
            return;
        }
        
        ContentTab tab = new ContentTab(compositeId, parentId, title, appBox, this);
        
        // Add to global tracking
        allTabs.put(compositeId, tab);
     
     
        // Display in this window
        displayTab(tab);
    }




    @Override
    public double getTopBarHeight() {
        return topBar.getHeight();
    }
    
    public Collection<ContentTab> getAllTabs() {
        return allTabs.values();
    }

     /**
     * Get all tabs in the primary (main) window
     */
    public List<ContentTab> getTabsInPrimaryWindow() {
        return allTabs.values().stream()
            .filter(ContentTab::isInPrimaryWindow)
            .collect(Collectors.toList());
    }

    /**
     * Get all tabs in a specific window
     */
    public List<ContentTab> getTabsInWindow(TabWindow window) {
        return allTabs.values().stream()
            .filter(tab -> tab.getParentWindow() == window)
            .collect(Collectors.toList());
    }

    /**
     * Get all unique detached windows
     */
    public Set<TabWindow> getAllDetachedWindows() {
        return allTabs.values().stream()
            .map(ContentTab::getParentWindow)
            .filter(window -> window != null && !window.isPrimaryWindow())
            .collect(Collectors.toSet());
    }


    /**
     * Find detached window at screen coordinates
     */
    public TabWindow findWindowAt(double screenX, double screenY) {
        // Check primary window first
        if (containsPoint(screenX, screenY)) {
            return this;
        }
        
        // Check detached windows
        return getAllDetachedWindows().stream()
            .filter(window -> window.containsPoint(screenX, screenY))
            .findFirst()
            .orElse(null);
    }

    @Override
    public void displayTab(ContentTab tab) {
        if(tab == null){
            return;
        }
        //
        NoteBytesArray tabId = tab.getId();

        ContentBox appBox = tab.getAppBox();

        DeferredLayoutManager.register(getStage(), appBox, _ -> {
            if (m_currentTabIdProperty.get() != null && m_currentTabIdProperty.get().equals(tabId)) {
                return new LayoutData.Builder()
                    .width(contentWidth)
                    .height(contentHeight)
                    .build();
            }
            return new LayoutData.Builder().build();
        });

        topBar.addTab(tab);
     
        tab.onCloseBtn(_ -> removeTab(tabId)); // Use closeTab
      

        tab.setCurrentIdProperty(m_currentTabIdProperty);

    }

     /**
     * Actually close/delete a tab permanently
     */
    public void removeTab(NoteBytesArray tabId) {
        ContentTab tab = allTabs.get(tabId);
        if (tab == null) return;
        
        TabWindow window = tab.getParentWindow();
        
        // Remove from display
        if (window != null) {
            window.undisplayTab(tabId);
        }
        
        // Shutdown the app box
        ContentBox appBox = tab.getAppBox();
        if (appBox != null) {
            appBox.shutdown();
        }
        
        // Remove from global tracking
        allTabs.remove(tabId);
        
        // Close empty detached windows
        if (window != null && !window.isPrimaryWindow() && isWindowEmpty(window)) {
            window.close();
        }
    }

    
    @Override
    public void undisplayTab(NoteBytesArray tabId) {
        // Only remove from visual display, don't delete
        ContentTab tab = allTabs.get(tabId);
        if (tab == null) return;

        NoteBytesArray currentTabId = m_currentTabIdProperty.get();

        boolean isCurrentTab = currentTabId != null && currentTabId.equals(tabId);
        
        if (isCurrentTab) {
            contentArea.getChildren().clear();
            m_currentTabIdProperty.set(null);
            
            // Switch to another tab if available in this window
            for (ContentTab t : getTabsInWindow(this)) {
                if (!t.getId().equals(tabId)) {
                    setCurrentTab(t.getId());
                    break;
                }
            }
        }
        ContentBox appBox = tab.getAppBox();
        DeferredLayoutManager.unregister(appBox);
        // Remove from top bar
        topBar.removeTab(tabId);
    }
    
    /**
     * Check if a window still has tabs
     */
    public boolean isWindowEmpty(TabWindow window) {
        return getTabsInWindow(window).isEmpty();
    }

    

     public void removeTab(NoteBytes id, NoteBytes parentId) {
        removeTab(new NoteBytesArray(id, parentId));
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


     public ContentBox[] getAppBoxesByParentId(NoteBytes parentId) {
        ArrayList<ContentBox> appBoxes = new ArrayList<>();
        for (Map.Entry<NoteBytesArray, ContentTab> entry : this.allTabs.entrySet()) {
            ContentTab tab = entry.getValue();
            if (parentId != null && parentId.equals(tab.getParentId())) {
                appBoxes.add((ContentBox)entry.getValue().getAppBox());
            } 
        }
        return appBoxes.toArray(new ContentBox[0]);
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
    
    @Override
    public void setCurrentTab(NoteBytesArray tabId) {
        m_currentTabIdProperty.set(tabId);
    }
    
    public NoteBytesArray getCurrentTabId() {
        return m_currentTabIdProperty.get();
    }
    
    public SideBarPanel getSideBar() {
        return sideBar;
    }
    
    public TabTopBar getTopBar() {
        return topBar;
    }
    
    @Override
    public void show() {
        stage.show();
    }
    
     @Override
    public void close() {
        if (m_onClose != null) {
            m_onClose.run();
        }
        stage.close();
    }
    
    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public boolean isPrimaryWindow() {
        return true;
    }

    @Override
    public boolean containsPoint(double screenX, double screenY) {
        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = topBar.getHeight();
        
        return screenX >= x && screenX <= x + w &&
               screenY >= y && screenY <= y + h;
    }
    
    public double getContentWidth() {
        return contentWidth;
    }
    
    public double getContentHeight() {
        return contentHeight;
    }

     // ==================== Tab Movement Methods ====================
    
    private void createDetachedWindow(NoteBytesArray tabId, double screenX, double screenY) {
        ContentTab tab = allTabs.get(tabId);
        if (tab == null) return;
        
        TabWindow oldWindow = tab.getParentWindow();
        
        // Remove from current window's display
        if (oldWindow != null) {
            oldWindow.undisplayTab(tabId);
        }
        
        // Create new detached window
        DetachedTabWindow window = new DetachedTabWindow(
            this,
            tab.getTitle(),
            FxResourceFactory.iconImage15,
            screenX - 100,
            screenY - 20
        );
        
        // Set tab's location and add to window
        tab.setParentWindow(window);
        tab.setCurrentIdProperty(window.currentIdProperty());
        window.displayTab(tab);
        
        window.show();
        
    }
    
    public void moveTabToWindow(NoteBytesArray tabId, TabWindow targetWindow) {
        ContentTab tab = allTabs.get(tabId);
        if (tab == null) return;
        
        TabWindow oldWindow = tab.getParentWindow();
        if (oldWindow == targetWindow) return; // Already there
        
        // Remove from old window's display
        if (oldWindow != null) {
            oldWindow.undisplayTab(tabId);
        }
        
        // Update tab's location
        tab.setParentWindow(targetWindow);
        
        // Add to new window
        targetWindow.displayTab(tab);
        
        // Check if old window is now empty and should close
        if (oldWindow != null && !oldWindow.isPrimaryWindow() && isWindowEmpty(oldWindow)) {
            oldWindow.close();
        }
    }

    // ==================== Drag and Drop ====================
    
    public void enableTabDragging() {
        for (ContentTab tab : allTabs.values()) {
            enableDragForTab(tab);
        }
    }
    
    private void enableDragForTab(ContentTab tab) {
        HBox tabBox = tab.getTabBox();
        final double[] dragStart = new double[2];
        final boolean[] isDragging = new boolean[1];
        final Stage[] ghostStage = new Stage[1];
        
        tabBox.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                dragStart[0] = e.getScreenX();
                dragStart[1] = e.getScreenY();
                isDragging[0] = false;
                e.consume();
            }
        });
        
        tabBox.setOnMouseDragged(e -> {
            double deltaX = e.getScreenX() - dragStart[0];
            double deltaY = e.getScreenY() - dragStart[1];
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            
            if (!isDragging[0] && distance > 15) {
                isDragging[0] = true;
                ghostStage[0] = createGhostTab(tab, e.getScreenX(), e.getScreenY());
            }
            
            if (isDragging[0] && ghostStage[0] != null) {
                ghostStage[0].setX(e.getScreenX() - 50);
                ghostStage[0].setY(e.getScreenY() - 15);
            }
            e.consume();
        });
        
        tabBox.setOnMouseReleased(e -> {
            if (isDragging[0]) {
                handleTabDrop(tab, e.getScreenX(), e.getScreenY());
                if (ghostStage[0] != null) {
                    ghostStage[0].close();
                }
            }
            isDragging[0] = false;
            e.consume();
        });
    }
    
    private Stage createGhostTab(ContentTab tab, double x, double y) {
        Stage ghost = new Stage(StageStyle.TRANSPARENT);
        ghost.setAlwaysOnTop(true);
        ghost.initOwner(stage);
        
        Label ghostLabel = new Label(tab.getTitle());
        ghostLabel.setStyle(
            "-fx-background-color: rgba(70, 70, 70, 0.9); " +
            "-fx-padding: 5px 15px; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 3px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 2);"
        );
        
        Scene ghostScene = new Scene(new StackPane(ghostLabel));
        ghostScene.setFill(null);
        ghostScene.setFill(Color.TRANSPARENT);
        ghost.setScene(ghostScene);
        ghost.setX(x);
        ghost.setY(y);
        ghost.show();
        
        return ghost;
    }
    
    private void handleTabDrop(ContentTab tab, double screenX, double screenY) {
        NoteBytesArray tabId = tab.getId();
        
        // Find which window (if any) we're dropping on
        TabWindow targetWindow = findWindowAt(screenX, screenY);
        
        if (targetWindow != null) {
            // Dropped on a window's top bar
            moveTabToWindow(tabId, targetWindow);
        } else {
            // Dropped in empty space - create new detached window
            createDetachedWindow(tabId, screenX, screenY);
        }
    }

    @Override
    public SimpleObjectProperty<NoteBytesArray> currentIdProperty() {
        return m_currentTabIdProperty;
    }
}