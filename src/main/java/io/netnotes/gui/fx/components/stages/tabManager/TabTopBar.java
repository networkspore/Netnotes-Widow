package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.noteBytes.NoteBytes;

public class TabTopBar extends HBox {
    private double xOffset = 0;
    private double yOffset = 0;
    private final MenuButton tabsMenuButton;
    private final HashMap<NoteBytes, MenuItem> tabMenuItems;
    private final HashMap<NoteBytes, ContentTab> openTabs;
    private TabSelectionListener tabSelectionListener;
    
    public interface TabSelectionListener {
        void onTabSelected(NoteBytes tabId);
    }
    
    public TabTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage) {
        super();
        this.tabMenuItems = new HashMap<>();
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
        
        // Tabs menu button
        tabsMenuButton = new MenuButton("Open Tabs");
        tabsMenuButton.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; " +
                               "-fx-padding: 3px 10px; -fx-font-size: 11px;");
        tabsMenuButton.setVisible(false); // Hidden until tabs are added
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Minimize button
        Button minimizeBtn = new Button("−");
        minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                           "-fx-font-size: 20px; -fx-padding: 0px 15px;");
        minimizeBtn.setOnAction(e -> theStage.setIconified(true));
        
        // Maximize button
        Button maxBtn = new Button("□");
        maxBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                       "-fx-font-size: 16px; -fx-padding: 0px 15px;");
        maxBtn.setOnAction(e -> theStage.setMaximized(!theStage.isMaximized()));
        
        this.getChildren().addAll(barIconView, titleLabel, spacer, tabsMenuButton, 
                                  minimizeBtn, maxBtn, closeBtn);
        
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
    }
    
    public void addTab(ContentTab tab) {
        openTabs.put(tab.getId(), tab);
        
        MenuItem menuItem = new MenuItem(tab.getTitle());
        menuItem.setStyle("-fx-padding: 5px 10px;");
        menuItem.setOnAction(e -> {
            if (tabSelectionListener != null) {
                tabSelectionListener.onTabSelected(tab.getId());
            }
        });
        
        tabMenuItems.put(tab.getId(), menuItem);
        updateTabsMenu();
    }
    
    public void removeTab(NoteBytes tabId) {
        openTabs.remove(tabId);
        MenuItem menuItem = tabMenuItems.remove(tabId);
        if (menuItem != null) {
            tabsMenuButton.getItems().remove(menuItem);
        }
        updateTabsMenu();
    }
    
    public void setActiveTab(NoteBytes tabId) {
        // Update menu items to show active tab
        for (Map.Entry<NoteBytes, MenuItem> entry : tabMenuItems.entrySet()) {
            if (entry.getKey().equals(tabId)) {
                entry.getValue().setStyle("-fx-padding: 5px 10px; -fx-background-color: #4a4a4a;");
            } else {
                entry.getValue().setStyle("-fx-padding: 5px 10px;");
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
        for (MenuItem item : tabMenuItems.values()) {
            tabsMenuButton.getItems().add(item);
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
}