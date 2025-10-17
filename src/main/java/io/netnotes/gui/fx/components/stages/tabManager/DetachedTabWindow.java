package io.netnotes.gui.fx.components.stages.tabManager;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
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
    private final TabManagerStage manager;
    private final HBox topBar;
    private final HBox tabsBox;
    private final StackPane contentArea;
    
    private final SimpleObjectProperty< NoteBytesArray> m_currentTabIdProperty = new SimpleObjectProperty<>(null);
    
    public DetachedTabWindow(TabManagerStage manager, String initialTitle, 
                            Image icon, double x, double y) {
        this.manager = manager;
        this.stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setX(x);
        stage.setY(y);
        
        // Simple top bar
        topBar = new HBox(5);
        topBar.setPadding(new Insets(7, 8, 3, 10));
        topBar.setId("topBar");
        
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        
        // Tabs display
        tabsBox = new HBox(5);
        tabsBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tabsBox, Priority.ALWAYS);
        
        // Close button
        BufferedButton closeBtn = new BufferedButton(FxResourceFactory.closeImg, 20);
        closeBtn.setOnAction(e -> close());
        
        topBar.getChildren().addAll(iconView, tabsBox, closeBtn);
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
    }
    
    // ==================== TabWindow Interface Implementation ====================
    
    @Override
    public SimpleObjectProperty<NoteBytesArray> currentIdProperty(){
        return m_currentTabIdProperty;
    }
    
     @Override
    public void displayTab(ContentTab tab) {
        NoteBytesArray tabId = tab.getId();
        
        // Add visual tab to topBar
        HBox tabBox = tab.getTabBox();
        if (!tabsBox.getChildren().contains(tabBox)) {
            tabsBox.getChildren().add(tabBox);
        }
        
        // Setup handlers
        tab.onTabClicked(e -> setCurrentTab(tabId));
        tab.onCloseBtn(e -> manager.removeTab(tabId)); // Use closeTab
        
        // Show this tab
        setCurrentTab(tabId);
    }

    @Override
    public void undisplayTab(NoteBytesArray tabId) {
        ContentTab tab = manager.getTab(tabId);
        if (tab == null) return;
        
        // Remove from visual display
        tabsBox.getChildren().remove(tab.getTabBox());
        
        List<ContentTab> remainingTabs = manager.getTabsInWindow(this);
        boolean isEmpty = remainingTabs.isEmpty();

        NoteBytesArray currentTabId = m_currentTabIdProperty.get();

        // Clear content if this was current
        if (tabId.equals(currentTabId)) {
            contentArea.getChildren().clear();
           
            
            setCurrentTab(!isEmpty ? remainingTabs.get(0).getId() : null);
          
        }

        if(isEmpty){
            stage.close();
        }
    }
    
    
    @Override
    public void setCurrentTab(NoteBytesArray tabId) {
        if (tabId == null){
            m_currentTabIdProperty.set(null);
            contentArea.getChildren().clear();
            return;
        }

        ContentTab tab = manager.getTab(tabId);

        if (tab == null || tab.getParentWindow() != this) return;
        
        
        m_currentTabIdProperty.set(tabId);
        
        contentArea.getChildren().clear();
        contentArea.getChildren().add(tab.getAppBox());
        
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