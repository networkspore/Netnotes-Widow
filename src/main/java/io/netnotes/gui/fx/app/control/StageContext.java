package io.netnotes.gui.fx.app.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Context provided to stage layout callbacks
 */
class StageContext {
    private final Map<Stage, StageNode> allStages;
    private final Stage currentStage;
    
    public StageContext(Map<Stage, StageNode> allStages, Stage currentStage) {
        this.allStages = allStages;
        this.currentStage = currentStage;
    }
    
    public Stage getCurrentStage() {
        return currentStage;
    }
    
    public Rectangle2D getStageBounds(Stage stage) {
        return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }
    
    public Screen getPrimaryScreen() {
        return Screen.getPrimary();
    }
    
    public List<Stage> getAllStages() {
        return new ArrayList<>(allStages.keySet());
    }
}