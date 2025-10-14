package io.netnotes.gui.fx.app.control;


/**
 * Data returned from stage layout calculation
 */
class StageLayout {
    private final double x, y, width, height;
    
    private StageLayout(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.width = builder.width;
        this.height = builder.height;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    
    public static class Builder {
        private double x, y, width, height;
        
        public Builder x(double x) {
            this.x = x;
            return this;
        }
        
        public Builder y(double y) {
            this.y = y;
            return this;
        }
        
        public Builder width(double width) {
            this.width = width;
            return this;
        }
        
        public Builder height(double height) {
            this.height = height;
            return this;
        }
        
        public StageLayout build() {
            return new StageLayout(this);
        }
    }
}