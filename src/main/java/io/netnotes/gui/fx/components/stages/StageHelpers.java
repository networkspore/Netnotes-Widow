package io.netnotes.gui.fx.components.stages;

import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class StageHelpers {
      public static void centerStage(Stage stage, Rectangle screenRectangle){
        stage.setX(screenRectangle.getWidth()/2 - stage.getWidth()/2);
        stage.setY(screenRectangle.getHeight()/2 - stage.getHeight()/2);
    }
}
