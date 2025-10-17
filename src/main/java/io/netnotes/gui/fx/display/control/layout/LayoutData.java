package io.netnotes.gui.fx.display.control.layout;

import javafx.scene.Node;

/**
 * Data returned from layout calculation
 */
public class LayoutData {
    private final double x, y, width, height;
    private final boolean setX, setY, setWidth, setHeight;
    
    private LayoutData(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.width = builder.width;
        this.height = builder.height;
        this.setX = builder.setX;
        this.setY = builder.setY;
        this.setWidth = builder.setWidth;
        this.setHeight = builder.setHeight;
    }
    
    public void applyTo(Node node) {
        if (setX) node.setLayoutX(x);
        if (setY) node.setLayoutY(y);
        if (setWidth && node.isResizable()) node.resize(width, node.getLayoutBounds().getHeight());
        if (setHeight && node.isResizable()) node.resize(node.getLayoutBounds().getWidth(), height);
    }
    
    public static class Builder {
        private double x, y, width, height;
        private boolean setX, setY, setWidth, setHeight;
        
        public Builder x(double x) {
            this.x = x;
            this.setX = true;
            return this;
        }
        
        public Builder y(double y) {
            this.y = y;
            this.setY = true;
            return this;
        }
        
        public Builder width(double width) {
            this.width = width;
            this.setWidth = true;
            return this;
        }
        
        public Builder height(double height) {
            this.height = height;
            this.setHeight = true;
            return this;
        }
        
        public LayoutData build() {
            return new LayoutData(this);
        }
    }
}