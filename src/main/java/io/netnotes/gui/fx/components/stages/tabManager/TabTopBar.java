package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.app.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.components.buttons.IconButton;

public class TabTopBar extends HBox {
    private double xOffset = 0;
    private double yOffset = 0;
    private final MenuButton tabsMenuButton;
    private HBox m_tabsBox;
    private ScrollPane m_tabsScroll;
    private final HashMap<NoteBytesArray, ContentTab> openTabs;
    private TabSelectionListener tabSelectionListener;
    private ScrollPaneHelper scrollHelper;
    public interface TabSelectionListener {
        void onTabSelected(NoteBytes tabId);
    }
    
    public TabTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage) {
        super();
   
        this.openTabs = new HashMap<>();
        
        this.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3c3c3c; " +
                     "-fx-border-width: 0 0 1 0;");
        this.setPadding(new Insets(7, 8, 5, 10));
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(10);
        this.setPrefHeight(35);
        this.setMinHeight(35);
        this.setMaxHeight(35);
        
        // Icon
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);
        
        // Title
        Label titleLabel = new Label(titleString);
        titleLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        titleLabel.setPadding(new Insets(0, 0, 0, 10));

        m_tabsBox = new HBox();
        m_tabsBox.setAlignment(Pos.CENTER_LEFT);
        m_tabsBox.setSpacing(5);
        m_tabsBox.setPadding(new Insets(0, 5, 0, 5));
        m_tabsBox.setStyle("-fx-background-color: transparent;");

        m_tabsScroll = new ScrollPane(m_tabsBox);
        m_tabsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        m_tabsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        m_tabsScroll.setFitToHeight(true);

        StackPane titleOverlayPane = new StackPane();
        titleOverlayPane.setAlignment(Pos.CENTER_LEFT);
        titleOverlayPane.getChildren().addAll(titleLabel, m_tabsScroll);
        StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        StackPane.setAlignment(m_tabsScroll, Pos.CENTER_LEFT);

        

        Tooltip menuToolTip = new Tooltip("Open Tabs");
        menuToolTip.setShowDelay(Duration.millis(100));
        // Tabs menu button
        tabsMenuButton = new MenuButton();
        tabsMenuButton.setTooltip(menuToolTip);
        tabsMenuButton.setId("arrowMenuButton");
        tabsMenuButton.setVisible(false); // Hidden until tabs are added
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        closeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.CLOSE_ICON), 20));
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");



        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.MINIMIZE_ICON), 20));
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        Button maximizeBtn = new Button();
        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));
             
        this.getChildren().addAll(barIconView, titleOverlayPane, spacer, tabsMenuButton,
                          minimizeBtn, maximizeBtn, closeBtn);
        
        // Make window draggable
        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                xOffset = mouseEvent.getSceneX();
                yOffset = mouseEvent.getSceneY();
            }
        });
        
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (!theStage.isMaximized()) {
                    theStage.setX(mouseEvent.getScreenX() - xOffset);
                    theStage.setY(mouseEvent.getScreenY() - yOffset);
                }
            }
        });

        scrollHelper = new ScrollPaneHelper(
            theStage,
            m_tabsScroll,
            m_tabsBox,
            widthProperty(), // baseWidth
            heightProperty(),
            new DoubleExpression[] {
                new SimpleDoubleProperty(barIconView.getFitWidth() + minimizeBtn.getLayoutBounds().getWidth() +
                    maximizeBtn.getLayoutBounds().getWidth() + closeBtn.getLayoutBounds().getWidth() +
                    tabsMenuButton.getLayoutBounds().getWidth()), 
            },
            null
        );
    }

    public ScrollPaneHelper getScrollPaneHelper(){
        return scrollHelper;
    }
    
    public void addTab(ContentTab tab) {
        openTabs.put(tab.getId(), tab);
        
        
        MenuItem menuItem = tab.getMenuItem();
        
        menuItem.setOnAction(e -> {
            if (tabSelectionListener != null) {
                tabSelectionListener.onTabSelected(tab.getId());
            }
        });
        

        updateTabsMenu();
    }
    
    public ContentTab removeTab(NoteBytesArray tabId) {
        ContentTab tab = openTabs.remove(tabId);
        MenuItem menuItem = tab.getMenuItem();
        if (menuItem != null) {
            tabsMenuButton.getItems().remove(menuItem);
        }
        updateTabsMenu();
        return tab;
    }
    
    public void setActiveTab(NoteBytes tabId) {
        // Update menu items to show active tab
        for (Map.Entry<NoteBytesArray, ContentTab> entry : openTabs.entrySet()) {
            if (entry.getKey().equals(tabId)) {
                entry.getValue().getMenuItem().setStyle("-fx-padding: 5px 10px; -fx-background-color: #4a4a4a;");
            } else {
                entry.getValue().getMenuItem().setStyle("-fx-padding: 5px 10px;");
            }
        }
    }
    
    private void updateTabsMenu() {
        tabsMenuButton.getItems().clear();
        
        if (openTabs.isEmpty()) {
            tabsMenuButton.setVisible(false);
            return;
        }
        
        tabsMenuButton.setVisible(true);
        tabsMenuButton.setText("Open Tabs (" + openTabs.size() + ")");
        
        // Add all tab menu items
        for (ContentTab item : openTabs.values()) {
            tabsMenuButton.getItems().add(item.getMenuItem());
        }
        
        // Add separator and close all option if there are multiple tabs
        if (openTabs.size() > 1) {
            tabsMenuButton.getItems().add(new SeparatorMenuItem());
            MenuItem closeAllItem = new MenuItem("Close All Tabs");
            closeAllItem.setStyle("-fx-padding: 5px 10px; -fx-text-fill: #ff6666;");
            closeAllItem.setOnAction(e -> {
                if (tabSelectionListener != null) {
                    // Signal to close all tabs
                    tabSelectionListener.onTabSelected(null);
                }
            });
            tabsMenuButton.getItems().add(closeAllItem);
        }
    }
    
    public void setTabSelectionListener(TabSelectionListener listener) {
        this.tabSelectionListener = listener;
    }
    
    public int getTabCount() {
        return openTabs.size();
    }

    public Map<NoteBytesArray, ContentTab> getTabs(){
        return openTabs;
    }
}