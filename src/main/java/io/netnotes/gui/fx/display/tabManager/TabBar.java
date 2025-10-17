package io.netnotes.gui.fx.display.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.binding.DoubleExpression;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.gui.fx.components.menus.KeyMenuItem;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;

/**
 * Reusable tab bar component that handles tab display, scrolling, and menu
 * Used by both TabTopBar and DetachedTabWindow
 */
public class TabBar extends HBox {
    private static final NoteBytes CLOSE_TABS_KEY = new NoteString("closeTabs");
    private double xOffset = 0;
    private double yOffset = 0;

    private final TabManagerStage manager;
    private final TabWindow parentWindow;
    private final HBox tabsBox;
    private final ScrollPane tabsScroll;
    private final MenuButton tabsMenuButton;
    private ScrollPaneHelper scrollHelper;
    private SeparatorMenuItem separatorMenuItem = new SeparatorMenuItem();
    
    /**
     * Create a tab bar
     * @param manager The global tab manager
     * @param parentWindow The window this tab bar belongs to
     * @param baseWidth The total available width for layout
     * @param baseHeight The total available height for layout
     * @param leftWidths Array of widths to subtract from left side (e.g., icon width)
     * @param rightWidths Array of widths to subtract from right side (e.g., buttons)
     */
    public TabBar(TabManagerStage manager, 
                  TabWindow parentWindow,
                  DoubleExpression baseWidth, 
                  DoubleExpression baseHeight,
                  DoubleExpression[] leftWidths, 
                  DoubleExpression[] rightWidths) {
        super(5);
        this.manager = manager;
        this.parentWindow = parentWindow;
        
        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(this, Priority.ALWAYS);
        
        // Inner HBox to hold the actual tabs
        tabsBox = new HBox();
        tabsBox.setAlignment(Pos.CENTER_LEFT);
        tabsBox.setSpacing(5);
        tabsBox.setPadding(new Insets(0, 5, 0, 5));
        tabsBox.setStyle("-fx-background-color: transparent;");

        tabsBox.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                xOffset = mouseEvent.getSceneX();
                yOffset = mouseEvent.getSceneY();
            }
        });
        Stage theStage = parentWindow.getStage();
        tabsBox.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (!theStage.isMaximized()) {
                    theStage.setX(mouseEvent.getScreenX() - xOffset);
                    theStage.setY(mouseEvent.getScreenY() - yOffset);
                }
            }
        });

        // ScrollPane to make tabs scrollable
        tabsScroll = new ScrollPane(tabsBox);
        tabsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tabsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabsScroll.setFitToHeight(true);
        HBox.setHgrow(tabsScroll, Priority.ALWAYS);

        // Menu button for tab overflow/dropdown
        Tooltip menuToolTip = new Tooltip("Open Tabs");
        menuToolTip.setShowDelay(Duration.millis(100));

        tabsMenuButton = new MenuButton();
        tabsMenuButton.setTooltip(menuToolTip);
        tabsMenuButton.setId("arrowMenuButton");
        tabsMenuButton.setVisible(false); // Hidden until tabs are added
        
        getChildren().addAll(tabsScroll, tabsMenuButton);
        
        // Setup scroll helper for automatic sizing
        scrollHelper = new ScrollPaneHelper(
            parentWindow.getStage(),
            tabsScroll,
            tabsBox,
            baseWidth,
            baseHeight,
            leftWidths,
            rightWidths
        );
    }
    
    /**
     * Add a tab to this tab bar
     */
    public void addTab(ContentTab tab) {
        if (tab == null) return;

        TaskUtils.noDelay(noDelay -> {
            // Add visual tab to the HBox
            HBox tabBox = tab.getTabBox();
            if (!tabsBox.getChildren().contains(tabBox)) {
                tabsBox.getChildren().add(tabBox);
            }
            
            // Add to menu dropdown
            addToMenu(tab);
            
            // Update menu button visibility
            updateMenuButton();
        });
    }
    
    /**
     * Remove a tab from this tab bar
     */
    public ContentTab removeTab(NoteBytesArray tabId) {
        ContentTab tab = manager.getTab(tabId);
        if (tab == null) return null;
        
        TaskUtils.noDelay(noDelay -> {
            // Remove visual tab
            tabsBox.getChildren().remove(tab.getTabBox());
            
            // Remove from menu
            KeyMenuItem.removeKeyItem(tabsMenuButton.getItems(), tabId);
            
            // Update menu button visibility
            updateMenuButton();
        });
        return tab;
    }
    

    
    /**
     * Add a tab to the dropdown menu
     */
    private void addToMenu(ContentTab tab) {
        NoteBytesArray tabId = tab.getId();
        
        // Check if already exists
        KeyMenuItem itemExists = KeyMenuItem.getKeyMenuItem(tabsMenuButton.getItems(), tabId);
        if (itemExists != null) {
            return;
        }
        
        // Create menu item
        KeyMenuItem menuItem = tab.getMenuItem();
  
        // Find if "Close All" already exists
        KeyMenuItem existingCloseTabsItem = KeyMenuItem.getKeyMenuItem(
            tabsMenuButton.getItems(), CLOSE_TABS_KEY
        );
        
        if (existingCloseTabsItem != null) {
            // Add before separator (before "Close All")
            tabsMenuButton.getItems().add(tabsMenuButton.getItems().size() - 2, menuItem);
        } else {
            // Add at end
            tabsMenuButton.getItems().add(menuItem);
        }
    }
    
    /**
     * Update menu button visibility and "Close All" option
     */
    private void updateMenuButton() {
        int tabCount = manager.getTabsInWindow(parentWindow).size();
        
        if (tabCount > 1) {
            // Check if "Close All" already exists
            KeyMenuItem existingCloseTabsItem = KeyMenuItem.getKeyMenuItem(
                tabsMenuButton.getItems(), CLOSE_TABS_KEY
            );
            
            if (existingCloseTabsItem == null) {
                // Add "Close All" option
                KeyMenuItem closeAllItem = new KeyMenuItem(
                    CLOSE_TABS_KEY, 
                    new NoteString("Close All"), 
                    System.currentTimeMillis(), 
                    KeyMenuItem.VALUE_NOT_KEY
                );
                closeAllItem.setOnAction(e -> {
                    closeAllTabsInWindow();
                });
                tabsMenuButton.getItems().add(separatorMenuItem);
                tabsMenuButton.getItems().add(closeAllItem);
            }
        } else {
            // Remove "Close All" option
            KeyMenuItem.removeKeyItem(tabsMenuButton.getItems(), CLOSE_TABS_KEY);
            tabsMenuButton.getItems().remove(separatorMenuItem);
        }
        
        // Show menu button if there are tabs
        if (tabCount == 0) {
            tabsMenuButton.setVisible(false);
        } else {
            tabsMenuButton.setVisible(true);
        }
    }
    
    /**
     * Close all tabs in this window
     */
    private void closeAllTabsInWindow() {
        List<ContentTab> tabs = new ArrayList<>(manager.getTabsInWindow(parentWindow));
        for (ContentTab tab : tabs) {
            manager.removeTab(tab.getId());
        }
    }
    
    /**
     * Get the number of tabs in this tab bar
     */
    public int getTabCount() {
        return manager.getTabsInWindow(parentWindow).size();
    }
    
    /**
     * Get the scroll helper for this tab bar
     */
    public ScrollPaneHelper getScrollPaneHelper() {
        return scrollHelper;
    }
}