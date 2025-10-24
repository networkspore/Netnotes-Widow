package io.netnotes.gui.fx.display.tabManager;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.display.tabManager.ContentTab.TabBox;

public class TabTopBar extends HBox {

    private double xOffset = 0;
    private double yOffset = 0;

    private final TabBar tabBar;
    private final Stage stage;
    
    public interface TabSelectionListener {
        void onTabSelected(NoteBytes tabId);
    }
    
    public TabTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage, TabManagerStage manager) {
        super();
   
        stage = theStage;
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 3, 10));
        this.setId("topBar"); 

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
        minimizeBtn.setOnAction(_ -> {
           theStage.setIconified(true);
        });

        BufferedButton maximizeBtn = new BufferedButton(FxResourceFactory.maximizeImg, 20);
        maximizeBtn.setId("toolBtn");
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));
        maximizeBtn.setOnAction(_->stage.setMaximized(!stage.isMaximized()));
             
        HBox buttonsBox = new HBox( minimizeBtn, maximizeBtn, closeBtn);
        buttonsBox.setMaxWidth(Region.USE_PREF_SIZE);
        buttonsBox.setMaxHeight(Region.USE_PREF_SIZE);
        
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
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, e ->setOffset(e));
        this.addEventFilter(MouseEvent.MOUSE_DRAGGED, e ->dragStage(e));
    }

    public void setOffset(MouseEvent mouseEvent){
        xOffset = mouseEvent.getSceneX();
        yOffset = mouseEvent.getSceneY();
    }

    public void dragStage(MouseEvent mouseEvent){
        if (!stage.isMaximized() && !(mouseEvent.getTarget() instanceof TabBox)) {
            stage.setX(mouseEvent.getScreenX() - xOffset);
            stage.setY(mouseEvent.getScreenY() - yOffset);
        }
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