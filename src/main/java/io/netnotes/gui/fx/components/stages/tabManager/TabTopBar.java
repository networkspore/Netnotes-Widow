package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.KeyMenuItem;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;

public class TabTopBar extends HBox {
    private final static NoteBytes CLOSE_TABS_KEY = new NoteString("closeTabs");
    private SeparatorMenuItem separatorMenuItem = new SeparatorMenuItem();
    

    private double xOffset = 0;
    private double yOffset = 0;
    private final MenuButton tabsMenuButton;
    private final HBox m_tabsBox;
    private final ScrollPane m_tabsScroll;
    private final ConcurrentHashMap<NoteBytesArray, ContentTab> allTabs;
    private TabSelectionListener tabSelectionListener;
    private ScrollPaneHelper scrollHelper;

    public interface TabSelectionListener {
        void onTabSelected(NoteBytes tabId);
    }
    
    public TabTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage, ConcurrentHashMap<NoteBytesArray, ContentTab> allTabs) {
        super();
   
        this.allTabs = allTabs;

        this.setAlignment(Pos.TOP_LEFT);
        this.setPadding(new Insets(7, 8, 3, 10));
        this.setId("topBar"); //-fx-background-color: linear-gradient(to bottom, #ffffff15 0%, #000000EE 50%, #11111110 90%);
        
     
        // Icon
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);
        barIconView.setMouseTransparent(true);
        
        // Title
        Label titleLabel = new Label(titleString);
        titleLabel.setFont(FxResourceFactory.titleFont);
        titleLabel.setTextFill(FxResourceFactory.txtColor);

        m_tabsBox = new HBox();
        m_tabsBox.setAlignment(Pos.CENTER_LEFT);
        m_tabsBox.setSpacing(5);
        m_tabsBox.setPadding(new Insets(0, 5, 0, 5));
        m_tabsBox.setStyle("-fx-background-color: transparent;");

        m_tabsScroll = new ScrollPane(m_tabsBox);
        m_tabsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        m_tabsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        m_tabsScroll.setFitToHeight(true);

        StackPane titleOverlayPane = new StackPane();
        HBox.setHgrow(titleOverlayPane, Priority.ALWAYS);
        titleOverlayPane.setAlignment(Pos.CENTER_LEFT);
        titleOverlayPane.getChildren().addAll(titleLabel, m_tabsScroll);
        StackPane.setAlignment(titleLabel, Pos.CENTER);
        StackPane.setAlignment(m_tabsScroll, Pos.CENTER_LEFT);

        
        Tooltip menuToolTip = new Tooltip("Open Tabs");
        menuToolTip.setShowDelay(Duration.millis(100));

        // Tabs menu button
        tabsMenuButton = new MenuButton();
        tabsMenuButton.setTooltip(menuToolTip);
        tabsMenuButton.setId("arrowMenuButton");
        tabsMenuButton.setVisible(false); // Hidden until tabs are added
        
  


        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");


        BufferedButton minimizeBtn = new BufferedButton(FxResourceFactory.minimizeImg, 20);
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
           theStage.setIconified(true);
        });

        BufferedButton maximizeBtn = new BufferedButton(FxResourceFactory.maximizeImg, 20);
        maximizeBtn.setId("toolBtn");
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));
             
        HBox buttonsBox = new HBox(tabsMenuButton, minimizeBtn, maximizeBtn, closeBtn);
        titleOverlayPane.getChildren().add(buttonsBox); 

        StackPane.setAlignment(m_tabsScroll, Pos.CENTER_RIGHT);

        this.getChildren().addAll(barIconView, titleOverlayPane);
        
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
                new SimpleDoubleProperty(barIconView.getFitWidth()),
                buttonsBox.widthProperty() 
            },
            new DoubleExpression[] {}
        );
    }

    public ScrollPaneHelper getScrollPaneHelper(){
        return scrollHelper;
    }
    
    public void addTab(ContentTab tab) {
        if(tab == null){
            return;
        }
        NoteBytesArray tabId = tab.getId();

        ContentTab result = allTabs.putIfAbsent(tabId, tab);
        
        if(result == null){
            
            TaskUtils.noDelay(noDelay->{
    

                KeyMenuItem itemExists = KeyMenuItem.getKeyMenuItem(tabsMenuButton.getItems(), tabId);

                if(itemExists != null){
                    return;
                }

                KeyMenuItem menuItem = tab.getMenuItem();
                menuItem.setOnAction(e -> {
                    if (tabSelectionListener != null) {
                        tabSelectionListener.onTabSelected(tabId);
                    }
                });

            
                
                KeyMenuItem existingCloseTabsItem = KeyMenuItem.getKeyMenuItem(tabsMenuButton.getItems(), CLOSE_TABS_KEY);

                if(existingCloseTabsItem != null){
                    //add before separator
                    tabsMenuButton.getItems().add(tabsMenuButton.getItems().size()-2, menuItem);
                }else{
                    //add at end
                    tabsMenuButton.getItems().add(menuItem);
                }
                checkMenuButtonSize(existingCloseTabsItem);
            });
        }
    }
    
    public ContentTab removeTab(NoteBytesArray tabId) {
        
        ContentTab tab = allTabs.remove(tabId);    
        if(tab!= null){
            TaskUtils.noDelay(noDelay->{
                KeyMenuItem.removeKeyItem(tabsMenuButton.getItems(), tabId);
                checkMenuButtonSize();
            });
        }
        return tab;
    }

   

    
    public KeyMenuItem removeMenuItem(NoteBytesArray tabId){
        return KeyMenuItem.removeKeyItem(tabsMenuButton.getItems(), tabId);
    }

    private void checkMenuButtonSize(){
         KeyMenuItem existingCloseTabsItem = KeyMenuItem.getKeyMenuItem(tabsMenuButton.getItems(), CLOSE_TABS_KEY);
         checkMenuButtonSize(existingCloseTabsItem);
    }

    private void checkMenuButtonSize(KeyMenuItem existingCloseTabs){
        if (allTabs.size() > 1) {
           
            if(existingCloseTabs == null){
                KeyMenuItem closeAllItem = new KeyMenuItem(CLOSE_TABS_KEY, new NoteString("Close All"), System.currentTimeMillis(), KeyMenuItem.VALUE_NOT_KEY);
                closeAllItem.setOnAction(e -> {
                    if (tabSelectionListener != null) {
                        // Signal to close all tabs
                        tabSelectionListener.onTabSelected(null);
                    }
                });
                tabsMenuButton.getItems().add(separatorMenuItem);
                tabsMenuButton.getItems().add(closeAllItem);

            }
        }else{
            tabsMenuButton.setVisible(true);
            KeyMenuItem.removeKeyItem(tabsMenuButton.getItems(), CLOSE_TABS_KEY);
            tabsMenuButton.getItems().remove(separatorMenuItem);
        }

        if (allTabs.isEmpty()) {
            tabsMenuButton.setVisible(false);
        }else{
            tabsMenuButton.setVisible(true);
        }
    }
    
    
    public void setTabSelectionListener(TabSelectionListener listener) {
        this.tabSelectionListener = listener;
    }
    
    public int getTabCount() {
        return allTabs.size();
    }

  
}