package io.netnotes.gui.fx.display.tabManager;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.FrameRateMonitor;
import io.netnotes.gui.fx.utils.TaskUtils;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

public class SideBarButton extends VBox {
    private final BufferedButton m_button;
    private final String m_title;
    private boolean m_isExpanded = false;
    private Node m_content;
    private boolean m_isInitialized = false;
    private Consumer<Boolean> m_onExpandedChanged;

    public SideBarButton(Image appIcon, String name){
        super();
        Tooltip tooltip = new Tooltip(name);
        tooltip.setShowDelay(javafx.util.Duration.millis(100));

        m_button = new BufferedButton(appIcon, FxResourceFactory.BTN_IMG_SIZE);
        m_button.setContentDisplay(ContentDisplay.LEFT);
        m_button.setTooltip(tooltip);
        m_button.setMaxWidth(Double.MAX_VALUE);
        m_button.setId("menuTabBtn");
        
        m_title = name;

        super.getChildren().add(m_button);
        setAlignment(Pos.TOP_LEFT);
        
        // Ensure button fills available width
        this.setFillWidth(true);
   
    }


    @Override 
    public ObservableList<Node> getChildren() {
        throw new IllegalStateException("Cannot access children");
    }

    public String getTitle(){
        return m_title;
    }

    public BufferedButton getButton(){
        return m_button;
    }

    public void setOnAction(EventHandler<ActionEvent> onAction){
        m_button.setOnAction(onAction);
    }

    public void setContent(Node content){
        m_content = content;
        if(m_isInitialized){
            updateIsExpanded(m_isExpanded);
        }
    }

    public Node getContent(){
        return m_content;
    }

    public boolean isInitialized(){
        return m_isInitialized;
    }

    public void setOnExpandedChanged(Consumer<Boolean> onExpandedChanged){
        m_onExpandedChanged = onExpandedChanged;
    }

    public CompletableFuture<Void> updateIsExpanded(boolean isExpanded) {
        m_isInitialized = true;
        m_isExpanded = isExpanded;

        CompletableFuture<Void> task = CompletableFuture
            .runAsync(() -> {
                try {
                    Thread.sleep(FrameRateMonitor.getInstance().getRecommendedDebounceDelay());
                    TaskUtils.noDelay(e -> {
                        if (isExpanded) {
                            // Expanded state
                            m_button.setText(m_title);
                           
                            m_button.setContentDisplay(ContentDisplay.LEFT);
                            
                            // Let parent container control width
                            this.setPrefWidth(Region.USE_COMPUTED_SIZE);
                            this.setMinWidth(SideBarPanel.DEFAULT_LARGE_WIDTH - 10);
                            this.setMaxWidth(Double.MAX_VALUE);
                            
                            // Clear fixed height
                            this.setPrefHeight(Region.USE_COMPUTED_SIZE);
                            this.setMinHeight(Region.USE_PREF_SIZE);
                            this.setMaxHeight(Double.MAX_VALUE);
                            
                            m_button.setPrefHeight(Region.USE_COMPUTED_SIZE);
                            m_button.setMinHeight(SideBarPanel.DEFAULT_SMALL_WIDTH);
                            
                            addContent();
                        } else {
                            // Collapsed state
                            removeContent();
                            m_button.setText(null);
                          
                            m_button.setContentDisplay(ContentDisplay.CENTER);
                            
                            // Fixed size when collapsed
                            double size = SideBarPanel.DEFAULT_SMALL_WIDTH - 5;
                            this.setPrefWidth(size);
                            this.setMinWidth(size);
                            this.setMaxWidth(size);
                            this.setPrefHeight(size);
                            this.setMinHeight(size);
                            this.setMaxHeight(size);
                            
                            m_button.setPrefWidth(size);
                            m_button.setPrefHeight(size);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Button update cancelled");
                }
            }, TaskUtils.getVirtualExecutor())
            .thenRunAsync(()->{
                if(m_onExpandedChanged != null){
                    m_onExpandedChanged.accept(isExpanded);
                }
            }, TaskUtils.getVirtualExecutor());

        return task;
    }

    private void addContent(){
        if(m_content != null && !super.getChildren().contains(m_content)){
            super.getChildren().add(m_content);
        }
    }

    private void removeContent(){
        if(m_content != null){
           super.getChildren().remove(m_content);
        }
    }
    
    /**
     * Manually update width based on parent container
     * Called by SideBarPanel when scrollbar visibility changes
     */
    public void updateWidth(double containerWidth) {
        if (m_isExpanded && containerWidth > 0) {
            this.setPrefWidth(containerWidth);
            this.setMaxWidth(containerWidth);
        }
    }
}