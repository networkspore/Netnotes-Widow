package io.netnotes.gui.fx.app.control;

import java.util.ArrayList;
import java.util.List;

import javafx.stage.Stage;

/**
 * Represents a stage in the stage positioning hierarchy
 */
class StageNode {
    private final Stage stage;
    private final StageLayoutCallback callback;
    private final List<StageNode> dependencies = new ArrayList<>();
    private StageLayout calculatedLayout;
    
    public StageNode(Stage stage, StageLayoutCallback callback) {
        this.stage = stage;
        this.callback = callback;
    }
    
    public void addDependency(StageNode dependency) {
        dependencies.add(dependency);
    }
    
    public void calculate(StageContext context) {
        if (callback != null) {
            calculatedLayout = callback.calculate(context);
        }
    }
    
    public void apply() {
        if (calculatedLayout != null) {
            stage.setX(calculatedLayout.getX());
            stage.setY(calculatedLayout.getY());
            if (calculatedLayout.getWidth() > 0) {
                stage.setWidth(calculatedLayout.getWidth());
            }
            if (calculatedLayout.getHeight() > 0) {
                stage.setHeight(calculatedLayout.getHeight());
            }
            calculatedLayout = null;
        }
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public List<StageNode> getDependencies() {
        return dependencies;
    }
}