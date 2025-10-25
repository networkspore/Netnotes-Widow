package io.netnotes.gui.fx.display.contentManager;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.StageLayout;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DetachedTabWindow implements TabWindow {
    private final Stage stage;
    private final AppManagerStage manager;
    private final HBox topBar;
    private final TabBar tabBar;
    private final StackPane contentArea;
    
    
    private final SimpleObjectProperty< NoteBytesArray> m_currentTabIdProperty = new SimpleObjectProperty<>(null);
    
   public DetachedTabWindow(AppManagerStage manager, String initialTitle, 
                            Image icon, double x, double y) {
      this.manager = manager;
        this.stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setX(x);
        stage.setY(y);

        DeferredLayoutManager.registerStage(
            stage, 
            _ -> {
                // Callback for stage-level positioning
                // Could implement snapping, cascading, etc.
                return new StageLayout.Builder()
                    .x(stage.getX())
                    .y(stage.getY())
                    .width(stage.getWidth())
                    .height(stage.getHeight())
                    .build();
            },
            manager.getStage()  // Optional: Dependency on main stage
        );
        
        // Top bar container
        topBar = new HBox(5);
        topBar.setPadding(new Insets(7, 8, 3, 10));
        topBar.setId("topBar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        
        // Icon
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        iconView.setPreserveRatio(true);
        
        // Close button
        BufferedButton closeBtn = new BufferedButton(FxResourceFactory.closeImg, 20);
        closeBtn.setOnAction(_ -> close());
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");
        
        // Create reusable tab bar
        tabBar = new TabBar(
            manager,
            this, // 'this' DetachedTabWindow implements TabWindow
            new SimpleDoubleProperty(800), // estimate width
            new SimpleDoubleProperty(40),  // estimate height
            new DoubleExpression[] {
                new SimpleDoubleProperty(iconView.getFitWidth())
            },
            new DoubleExpression[] {
                new SimpleDoubleProperty(closeBtn.getWidth() + 10)
            }
        );
        HBox.setHgrow(tabBar, Priority.ALWAYS);
        
        topBar.getChildren().addAll(iconView, tabBar, closeBtn);
        makeDraggable();
        
        // Content area
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #1e1e1e;");
        
       
        
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(contentArea);
        
        Scene scene = new Scene(root, 800, 600);
        scene.setFill(null);
        scene.getStylesheets().add(FxResourceFactory.DEFAULT_CSS);
        stage.setScene(scene);

        m_currentTabIdProperty.addListener((_, _, newval)->{
            if (newval == null){
                contentArea.getChildren().clear();
                return;
            }

            ContentTab tab = manager.getTab(newval);

            if (tab == null || tab.getParentWindow() != this) return;
        
            contentArea.getChildren().clear();
            contentArea.getChildren().add(tab.getAppBox());

            DeferredLayoutManager.markDirty(tab.getAppBox());
        });
    }

    public double getContentWidth(){
        return contentArea.getLayoutBounds().getWidth();
    }

    public double getContentHeight(){
        return contentArea.getLayoutBounds().getHeight();
    }
    
    // ==================== TabWindow Interface Implementation ====================
    
    @Override
    public SimpleObjectProperty<NoteBytesArray> currentIdProperty(){
        return m_currentTabIdProperty;
    }
    
     @Override
    public void displayTab(ContentTab tab) {
        if(tab == null){
            return;
        }

        NoteBytesArray tabId = tab.getId();
        AppBox appBox = tab.getAppBox();
    
        tabBar.addTab(tab);
   
 
        tab.onCloseBtn(_ -> manager.removeTab(tabId)); // Use closeTab
        
 

        DeferredLayoutManager.register(getStage(), appBox, _ -> {
            if (m_currentTabIdProperty.get() != null && m_currentTabIdProperty.get().equals(tabId)) {
                return new LayoutData.Builder()
                    .width(contentArea.getLayoutBounds().getWidth())
                    .height(contentArea.getLayoutBounds().getHeight())
                    .build();
            }
            return new LayoutData.Builder().build();
        });
            
        tab.setCurrentIdProperty(m_currentTabIdProperty);
    }

    @Override
    public void undisplayTab(NoteBytesArray tabId) {
        if(tabId == null){
            return;
        }
        // Remove from tab bar
        ContentTab tab = tabBar.removeTab(tabId);
        
        // Clear content if this was current
        if (tabId.equals(m_currentTabIdProperty.get())) {

            setCurrentTab(null);
            
            // Switch to another tab if available
            List<ContentTab> remainingTabs = manager.getTabsInWindow(this);
            if (!remainingTabs.isEmpty()) {
                setCurrentTab(remainingTabs.get(0).getId());
            }
        }

        AppBox appBox = tab.getAppBox();
        DeferredLayoutManager.unregister(appBox);

        if(tab != null && m_currentTabIdProperty == tab.currentIdProperty()){
            tab.setCurrentIdProperty(null);
        }
    }
    
    
    @Override
    public void setCurrentTab(NoteBytesArray tabId) {
        m_currentTabIdProperty.set(tabId);
    }
    
    @Override
    public Stage getStage() {
        return stage;
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
    
    @Override
    public double getTopBarHeight() {
        return topBar.getHeight();
    }
    
    @Override
    public void show() {
        stage.show();
    }
    
    @Override
    public void close() {
        List<ContentTab> tabs = new ArrayList<>(manager.getTabsInWindow(this));
        for(ContentTab tab : tabs){
            NoteBytesArray tabId = tab.getId();
            undisplayTab(tabId);
            manager.removeTab(tabId);
        }
    }

    public void moveAllToPrimary(){
         // Move all tabs back to primary window
        List<ContentTab> tabs = new ArrayList<>(manager.getTabsInWindow(this));
        for (ContentTab tab : tabs) {
            manager.moveTabToWindow(tab.getId(), manager);
        }
    }
    
    @Override
    public boolean isPrimaryWindow() {
        return false;
    }
    
    // ==================== Window Dragging ====================
    
    private void makeDraggable() {
        final double[] offset = new double[2];
        
        topBar.setOnMousePressed(e -> {
            offset[0] = e.getSceneX();
            offset[1] = e.getSceneY();
        });
        
        topBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - offset[0]);
            stage.setY(e.getScreenY() - offset[1]);
        });
    }
}