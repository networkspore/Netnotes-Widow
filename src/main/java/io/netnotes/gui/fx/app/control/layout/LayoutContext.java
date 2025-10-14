package io.netnotes.gui.fx.app.control.layout;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Context provided to layout callbacks
 */
class LayoutContext {
    private final Node node;
    private final Stage stage;
    
    public LayoutContext(Node node, Stage stage) {
        this.node = node;
        this.stage = stage;
    }
    
    public Node getNode() {
        return node;
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public Node getParent() {
        return node.getParent();
    }
}