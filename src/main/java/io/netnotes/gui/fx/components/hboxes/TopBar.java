package io.netnotes.gui.fx.components.hboxes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import io.netnotes.gui.fx.components.buttons.IconButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.event.EventHandler;

public class TopBar extends HBox {

    static class Delta {
        double x, y;
    }

    public static ImageView highlightedImageView(Image image) {

        ImageView imageView = new ImageView(image);

        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-0.5);

        imageView.addEventFilter(MouseEvent.MOUSE_ENTERED, _ -> {

            imageView.setEffect(colorAdjust);

        });
        imageView.addEventFilter(MouseEvent.MOUSE_EXITED, _ -> {
            imageView.setEffect(null);
        });

        return imageView;
    }
    
    public TopBar(Image iconImage, Button fillRightBtn, Button maximizeBtn, Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(theStage.titleProperty().get());
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));
        newTitleLbl.textProperty().bind(theStage.titleProperty());

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        fillRightBtn.setId("toolBtn");
        fillRightBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.FILL_RIGHT_ICON), 20));
        fillRightBtn.setPadding(new Insets(0, 3, 0, 3));

        this.getChildren().addAll(barIconView, newTitleLbl, spacer, minimizeBtn, fillRightBtn, maximizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 10, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }


   

    public TopBar(Image iconImage, Button maximizeBtn, Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(theStage.titleProperty().get());
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));
        newTitleLbl.textProperty().bind(theStage.titleProperty());

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        this.getChildren().addAll(barIconView, newTitleLbl, spacer, minimizeBtn, maximizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 3, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }

    

    public TopBar(Image iconImage, String titleString, Button maximizeBtn, Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(FxResourceFactory.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        this.getChildren().addAll(barIconView, newTitleLbl, spacer, minimizeBtn, maximizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 10, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }

    public TopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });

        this.getChildren().addAll(barIconView, newTitleLbl, spacer, minimizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 5, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }


    public TopBar(Button extraBtn, Image iconImage, String titleString,  Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);


        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });

        this.getChildren().addAll(barIconView, newTitleLbl, spacer, extraBtn,  minimizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 5, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }

    public TopBar(Image iconImage, Label newTitleLbl, Button closeBtn, Stage theStage) {
        super();
        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        newTitleLbl.setFont(FxResourceFactory.titleFont);
        newTitleLbl.setTextFill(FxResourceFactory.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(FxResourceFactory.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(FxResourceFactory.minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(_ -> {
            theStage.setIconified(true);
        });
        
        this.getChildren().addAll(barIconView, newTitleLbl, spacer, minimizeBtn, closeBtn);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(7, 8, 10, 10));
        this.setId("topBar");

        Delta dragDelta = new Delta();

        this.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

    }
}
