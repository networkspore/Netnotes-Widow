package io.netnotes.gui.fx.display.tabManager;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.FrameRateMonitor;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.animation.Timeline;
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
    private AtomicBoolean m_isExpanded = new AtomicBoolean(false);
    private Node m_content;
    private boolean m_isInitialized = false;
    private BiConsumer<Boolean, AtomicBoolean> m_onExpandedChanged;

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

    public void setContent(Node content, AtomicBoolean isCancelled, Timeline panelTimeline){
        m_content = content;
        if(m_isInitialized){
            updateIsExpanded(m_isExpanded.get(),isCancelled, panelTimeline);
        }
    }

    public Node getContent(){
        return m_content;
    }

    public boolean isInitialized(){
        return m_isInitialized;
    }

    public void setOnExpandedChanged(BiConsumer<Boolean, AtomicBoolean> onExpandedChanged){
        m_onExpandedChanged = onExpandedChanged;
    }

    public CompletableFuture<Void> updateIsExpanded(boolean isExpanded, AtomicBoolean cancel, Timeline panelTimeline) {
        m_isInitialized = true;
        m_isExpanded.set(isExpanded);

        return CompletableFuture.runAsync(() -> {
            try {
                long debounce = FrameRateMonitor.getInstance().getRecommendedDebounceDelay();
                long step = 10;
                for (long elapsed = 0; elapsed < debounce; elapsed += step) {
                    if (cancel.get()) throw new InterruptedException("Cancelled");
                    Thread.sleep(step);
                }

                // Schedule UI update on FX thread
                TaskUtils.fxDelay(_ -> {
                    if (cancel.get()) return;

                    // Optional: adjust button width to match current panel progress
                    if (panelTimeline != null) {
                        double progress = panelTimeline.getCurrentTime().toMillis() / panelTimeline.getTotalDuration().toMillis();
                        double panelWidth = m_isExpanded.get() ? SideBarPanel.DEFAULT_SMALL_WIDTH + progress * (SideBarPanel.DEFAULT_LARGE_WIDTH - SideBarPanel.DEFAULT_SMALL_WIDTH)
                                                        : SideBarPanel.DEFAULT_LARGE_WIDTH - progress * (SideBarPanel.DEFAULT_LARGE_WIDTH - SideBarPanel.DEFAULT_SMALL_WIDTH);
                        this.setPrefWidth(panelWidth - 10);
                    }

                    if (isExpanded) {
                        m_button.setText(m_title);
                        m_button.setContentDisplay(ContentDisplay.LEFT);
                        this.setPrefWidth(Region.USE_COMPUTED_SIZE);
                        this.setMinWidth(SideBarPanel.DEFAULT_LARGE_WIDTH - 10);
                        this.setMaxWidth(Double.MAX_VALUE);
                        addContent();
                    } else {
                        removeContent();
                        m_button.setText(null);
                        m_button.setContentDisplay(ContentDisplay.CENTER);
                        double size = SideBarPanel.DEFAULT_SMALL_WIDTH - 5;
                        this.setPrefWidth(size);
                        this.setMinWidth(size);
                        this.setMaxWidth(size);
                        this.setPrefHeight(size);
                        this.setMinHeight(size);
                        this.setMaxHeight(size);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Button update cancelled");
            }
        }, TaskUtils.getVirtualExecutor()).thenAcceptAsync((_)->m_onExpandedChanged.accept(isExpanded, cancel));
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
        if (m_isExpanded.get() && containerWidth > 0) {
            this.setPrefWidth(containerWidth);
            this.setMaxWidth(containerWidth);
        }
    }
}