package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;

public class TabTopBar extends HBox {

    private double xOffset = 0;
    private double yOffset = 0;

    private final TabBar tabBar;

    
    public interface TabSelectionListener {
        void onTabSelected(NoteBytes tabId);
    }
    
    public TabTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage, TabManagerStage manager) {
        super();
   

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
             
        HBox buttonsBox = new HBox( minimizeBtn, maximizeBtn, closeBtn);

        tabBar = new TabBar(
            manager,
            manager, // TabManagerStage implements TabWindow
            widthProperty(),
            heightProperty(),
            new DoubleExpression[] {
                new SimpleDoubleProperty(barIconView.getFitWidth())
            },
            new DoubleExpression[] {
                buttonsBox.widthProperty()
            }
        );
        
        StackPane titleOverlayPane = new StackPane();
        HBox.setHgrow(titleOverlayPane, Priority.ALWAYS);
        titleOverlayPane.setAlignment(Pos.CENTER_LEFT);
        titleOverlayPane.getChildren().addAll(titleLabel, tabBar, buttonsBox);
        StackPane.setAlignment(titleLabel, Pos.CENTER);
        StackPane.setAlignment(tabBar, Pos.CENTER_LEFT);
        StackPane.setAlignment(buttonsBox, Pos.CENTER_RIGHT);


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

    }

    public ScrollPaneHelper getScrollPaneHelper() {
        return tabBar.getScrollPaneHelper();
    }
    
    public void addTab(ContentTab tab) {
        tabBar.addTab(tab);
    }


    public void removeTab(NoteBytesArray tabId) {
        tabBar.removeTab(tabId);
    }


    public int getTabCount() {
        return tabBar.getTabCount();
    }

  
}