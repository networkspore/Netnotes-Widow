package io.netnotes.gui.fx.display.control.layout;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Represents a node in the layout hierarchy
 */
public class LayoutNode {
    private final Node node;
    private final Stage stage;
    private final LayoutCallback callback;
    private final List<LayoutNode> children = new ArrayList<>();
    private LayoutNode parent;
    private LayoutData calculatedLayout;
    private int depth = -1;
    
    public LayoutNode(Node node, Stage stage, LayoutCallback callback) {
        this.node = node;
        this.stage = stage;
        this.callback = callback;
    }
    
    public void addChild(LayoutNode child) {
        children.add(child);
        child.parent = this;
    }
    
    public void calculate() {
        if (callback != null) {
            calculatedLayout = callback.calculate(new LayoutContext(node, stage));
        }
    }
    
    public void apply() {
        if (calculatedLayout != null) {
            calculatedLayout.applyTo(node);
            calculatedLayout = null;
        }
    }
    
    public Node getNode() {
        return node;
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public int getDepth() {
        if (depth == -1) {
            depth = calculateDepth();
        }
        return depth;
    }
    
    private int calculateDepth() {
        int d = 0;
        LayoutNode current = this.parent;
        while (current != null) {
            d++;
            current = current.parent;
        }
        return d;
    }
    
    public LayoutNode getParent() {
        return parent;
    }
    
    public List<LayoutNode> getChildren() {
        return children;
    }
}