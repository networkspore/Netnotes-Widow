package io.netnotes.gui.fx.display.tabManager;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.DoubleProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.BufferedMenuButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.FrameRateMonitor;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;

public class SideBarPanel extends VBox {
    public final static int DEFAULT_SMALL_WIDTH = 50;
    public final static int DEFAULT_LARGE_WIDTH = 200;
    public final static int BUTTON_SPACING = 5;
    public final static int PANEL_PADDING = 10;
    
    private final VBox m_buttonContainer;
    private final BufferedMenuButton m_settingsButton;
    private final BufferedButton m_expandButton;
    private final List<SideBarButton> m_buttons;
    private final ScrollPane m_listScroll;
    private final HBox m_listBoxPadding;
    private Timeline m_transitionTimeline = null;
    private long m_lastToggleTime = 0;
    private PauseTransition debounce = new PauseTransition(Duration.millis(TaskUtils.DEFAULT_FX_DELAY));
    
    private ScrollPaneHelper m_scrollHelper;
    private final AtomicReference<CompletableFuture<Void>> m_currentTask =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    private final AtomicReference<AtomicBoolean> cancelFlag = 
        new AtomicReference<>(new AtomicBoolean(false));

    private boolean isExpanded = false;
    private Stage stage;
    
    // Properties for dynamic sizing
    private final DoubleProperty availableWidth = new SimpleDoubleProperty();
    private final DoubleProperty availableHeight = new SimpleDoubleProperty();
    private final DoubleExpression topBarHeight;
    public SideBarPanel(DoubleExpression topBarHeight) {
        this.m_buttons = new ArrayList<>();

        this.topBarHeight = topBarHeight;
        
        this.setId("appMenuBox");
        this.setPrefWidth(DEFAULT_SMALL_WIDTH);
        this.setMinWidth(DEFAULT_SMALL_WIDTH);
        this.setMaxWidth(DEFAULT_SMALL_WIDTH);
        
        // Expand/collapse button
        m_expandButton = new BufferedButton(FxResourceFactory.TOGGLE_FRAME, FxResourceFactory.BTN_IMG_SIZE);
        m_expandButton.setId("menuTabBtn");
        m_expandButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        m_expandButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        m_expandButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        
        // Settings button
        m_settingsButton = new BufferedMenuButton(FxResourceFactory.SETTINGS_ICON, FxResourceFactory.BTN_IMG_SIZE);
        m_expandButton.setId("menuTabBtn");
        m_settingsButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        m_settingsButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        m_settingsButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        
        // Button container
        m_buttonContainer = new VBox(BUTTON_SPACING);
        HBox.setHgrow(m_buttonContainer, Priority.ALWAYS);
        m_buttonContainer.setPadding(new Insets(0, 0, 2, 0));
        m_buttonContainer.setAlignment(Pos.TOP_LEFT);
        
        // Padding container for button list
        m_listBoxPadding = new HBox(m_buttonContainer);
        m_listBoxPadding.setPadding(new Insets(2));
        
        // Scroll content
        VBox scrollContentBox = new VBox(m_listBoxPadding);
        
        // Scrollable list
        m_listScroll = new ScrollPane(scrollContentBox);
        m_listScroll.setFitToWidth(true);
        m_listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        m_listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        m_listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        this.getChildren().addAll(m_expandButton, m_listScroll, spacer, m_settingsButton);
        this.setPadding(new Insets(0, 0, PANEL_PADDING, 0));
        
        // Setup width property
        availableWidth.set(DEFAULT_SMALL_WIDTH);
    }
    
    /**
     * Initialize with stage reference and register with DeferredLayoutManager.
     * This should be called by TabManagerStage after construction.
     */
    public void initializeLayout(Stage stage) {
        this.stage = stage;
        
        // Register the sidebar panel itself
        DeferredLayoutManager.register(stage, this, _ -> {
            if (stage.getScene() == null) {
                return new LayoutData.Builder().build();
            }

            double sceneHeight = stage.getScene().getHeight();
            double topBarHeight = this.topBarHeight.doubleValue();
            double sidebarHeight = sceneHeight - topBarHeight;
            
            // Update available dimensions
            availableHeight.set(sidebarHeight);
            
            return new LayoutData.Builder()
                .height(sidebarHeight)
                .build();
        });
        
        // Create ScrollPaneHelper for proper scroll pane sizing
        DoubleExpression[] heightOffsets = {
            m_expandButton.heightProperty(),
            m_settingsButton.heightProperty(),
            new SimpleDoubleProperty(PANEL_PADDING + BUTTON_SPACING) // Padding and spacing
        };
        
        m_scrollHelper = new ScrollPaneHelper(
            stage,
            m_listScroll,
            m_listBoxPadding,
            availableWidth,
            availableHeight,
            new DoubleProperty[] { new SimpleDoubleProperty(4) }, // Width offset for padding
            heightOffsets
        );
        
        // Register button container to adapt to scroll pane content width
        DeferredLayoutManager.register(stage, m_buttonContainer, _ -> {
            // Calculate available width for buttons
            // Account for scrollbar when visible
            double containerWidth = availableWidth.get() - 8; // Padding adjustment
            
            // Check if scrollbar is visible
            if (m_listScroll.getVbarPolicy() == ScrollPane.ScrollBarPolicy.AS_NEEDED) {
                // If content height exceeds viewport, scrollbar will appear
                double contentHeight = m_buttonContainer.getHeight();
                double viewportHeight = m_listScroll.getViewportBounds().getHeight();
                
                if (contentHeight > viewportHeight && viewportHeight > 0) {
                    // Scrollbar is visible, reduce width
                    containerWidth -= 15; // Typical scrollbar width
                }
            }
            
            return new LayoutData.Builder()
                .width(Math.max(0, containerWidth))
                .build();
        });
        
        // Listen for scene height changes
        stage.getScene().heightProperty().addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(this);
        });
        
        // Listen for button container height changes (for scrollbar detection)
        m_buttonContainer.heightProperty().addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(m_buttonContainer);
            debounceButtonSizes();
        });
        
        // Listen for scroll pane viewport changes
        m_listScroll.viewportBoundsProperty().addListener((_, _, _) -> {
            DeferredLayoutManager.markDirty(m_buttonContainer);
            debounceButtonSizes();
        });
    }

    private void debounceButtonSizes(){
        debounce.setDuration(Duration.millis(FrameRateMonitor.getInstance().getRecommendedDebounceDelay()));
        debounce.setOnFinished(_ -> updateButtonSizes());
        debounce.playFromStart();
    }
    
    public void addButton(SideBarButton button) {
        button.setMaxWidth(Double.MAX_VALUE);
        m_buttons.add(button);
        m_buttonContainer.getChildren().add(button);
        button.updateIsExpanded(isExpanded, cancelFlag.get(), m_transitionTimeline);
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(m_buttonContainer);
        }
    }
    
    public void removeButton(SideBarButton button) {
        m_buttons.remove(button);
        m_buttonContainer.getChildren().remove(button);
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(m_buttonContainer);
        }
    }
    
    public void clearButtons() {
        m_buttons.clear();
        m_buttonContainer.getChildren().clear();
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(m_buttonContainer);
        }
    }
    
    public BufferedMenuButton getM_settingsButton() {
        return m_settingsButton;
    }
    
    public BufferedButton getM_expandButton() {
        return m_expandButton;
    }

    public void toggleExpanded() {
        double startWidth = getLayoutBounds().getWidth();
        isExpanded = !isExpanded;
        double endWidth = isExpanded ? DEFAULT_LARGE_WIDTH : DEFAULT_SMALL_WIDTH;

        // Cancel any running animation
        if (m_transitionTimeline != null) {
            m_transitionTimeline.stop();
        }

        // Adaptive duration
        long now = System.currentTimeMillis();
        long delta = now - m_lastToggleTime;
        m_lastToggleTime = now;
        double baseDuration = 250.0;
        double minDuration = 100.0;
        double adaptiveDuration = Math.max(minDuration, baseDuration - (150.0 - delta));
        double remainingFraction = Math.abs(startWidth - endWidth) / (DEFAULT_LARGE_WIDTH - DEFAULT_SMALL_WIDTH);
        double durationMs = adaptiveDuration * remainingFraction;

        // Create timeline
        m_transitionTimeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(prefWidthProperty(), startWidth)),
            new KeyFrame(Duration.millis(durationMs), new KeyValue(prefWidthProperty(), endWidth, Interpolator.EASE_BOTH))
        );

        m_transitionTimeline.currentTimeProperty().addListener((_, _, newTime) -> {
            double progress = newTime.toMillis() / durationMs;
            double width = startWidth + (endWidth - startWidth) * progress;
            setMinWidth(width);
            setMaxWidth(width);
            availableWidth.set(width);
            DeferredLayoutManager.markDirty(this);
        });

        m_transitionTimeline.setOnFinished(_ -> {
            availableWidth.set(endWidth);
            DeferredLayoutManager.markDirty(m_buttonContainer);
            m_scrollHelper.refresh();
            m_transitionTimeline = null;
        });

        m_transitionTimeline.play();

        // Cancel old async chain
        AtomicBoolean oldFlag = cancelFlag.getAndSet(new AtomicBoolean(false));
        oldFlag.set(true);
        AtomicBoolean myCancel = cancelFlag.get();

        // Start new async button updates
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (SideBarButton button : m_buttons) {
            chain = chain.thenComposeAsync(_ -> {
                if (myCancel.get()) return CompletableFuture.completedFuture(null);
                // Pass the current timeline progress to sync with animation
                return button.updateIsExpanded(isExpanded, myCancel, m_transitionTimeline);
            }, TaskUtils.getVirtualExecutor());
        }

        m_currentTask.set(chain);

        if (stage != null) {
            DeferredLayoutManager.markDirty(this);
            m_scrollHelper.refresh();
        }
    }


    public boolean isExpanded() {
        return isExpanded;
    }
    
    public List<SideBarButton> getM_buttons() {
        return new ArrayList<>(m_buttons);
    }
    
    /**
     * Update all button sizes based on current container width
     */
    private void updateButtonSizes() {
        if (m_buttonContainer.getWidth() > 0) {
            double buttonWidth = m_buttonContainer.getWidth() -1;
            
            for (SideBarButton button : m_buttons) {
                if (button.getHeight() <= 0) continue;
                button.setPrefWidth(buttonWidth);
                if (!isExpanded) {
                    button.setPrefHeight(DEFAULT_SMALL_WIDTH);
                }
            }
        }
    }
    
    /**
     * Get the current width of the button container accounting for scrollbar
     */
    public double getButtonContainerWidth() {
        return m_buttonContainer.getWidth();
    }
    
    /**
     * Check if scrollbar is currently visible
     */
    public boolean isScrollBarVisible() {
        if (m_listScroll.getViewportBounds().getHeight() > 0) {
            return m_buttonContainer.getHeight() > m_listScroll.getViewportBounds().getHeight();
        }
        return false;
    }
}