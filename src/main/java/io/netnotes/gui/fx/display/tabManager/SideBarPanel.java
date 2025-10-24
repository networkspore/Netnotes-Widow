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
import javafx.beans.value.ChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.FrameRateMonitor;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;

public class SideBarPanel extends VBox {
    private final String m_title;
    public final static int DEFAULT_SMALL_WIDTH = 50;
    public final static int DEFAULT_LARGE_WIDTH = 200;
    public final static int BUTTON_SPACING = 5;
    public final static int PANEL_PADDING = 10;
    private final static double TOGGLE_BASE_DURATION = 130.0;
    private final static double TOGGLE_MIN_DURATION = 25.0; // Fixed typo
    private final static double ADAPTIVE_DURATION_FACTOR = 0.3; // Factor for adaptive duration
    
    private double m_btnPadding = 10;
    private Insets m_btnInsets = new Insets(0,0,0,m_btnPadding);
    private Insets m_expandedBtnInsets = new Insets(0,m_btnPadding,0,m_btnPadding);
    private double m_btnTextGap = m_btnPadding; 

    private final VBox m_buttonContainer;
    private final BufferedButton m_settingsButton;
    private final BufferedButton m_expandButton;
    private final List<SideBarButton> m_buttons;
    private final ScrollPane m_listScroll;
    private final HBox m_listBoxPadding;
    private Timeline m_transitionTimeline = null;
    private long m_lastToggleTime = System.currentTimeMillis();
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
    
    // Listener references for cleanup
    private ChangeListener<Number> sceneHeightListener;
    private ChangeListener<Number> containerHeightListener;
    private ChangeListener<javafx.geometry.Bounds> viewportBoundsListener;
    
    public SideBarPanel(String title, DoubleExpression topBarHeight) {
        this.m_buttons = new ArrayList<>();
        this.topBarHeight = topBarHeight;
        this.m_title = title;
        
        this.setId("appMenuBox");
        this.setPrefWidth(DEFAULT_SMALL_WIDTH);
        this.setMinWidth(DEFAULT_SMALL_WIDTH);
        this.setMaxWidth(DEFAULT_SMALL_WIDTH);
        
        // Expand/collapse button
        m_expandButton = new BufferedButton(FxResourceFactory.TOGGLE_FRAME, FxResourceFactory.BTN_IMG_SIZE);
        m_expandButton.setId("logoTabBtn");
        m_expandButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        m_expandButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        m_expandButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        m_expandButton.setAlignment(Pos.CENTER_LEFT);
        m_expandButton.setFont(FxResourceFactory.HeadingFont);
        m_expandButton.setText(isExpanded ? m_title : "");
        m_expandButton.setPadding(isExpanded ? m_expandedBtnInsets : m_btnInsets);
        m_expandButton.setGraphicTextGap(isExpanded ? 15 : 0);
        
        // Settings button
        m_settingsButton = new BufferedButton(FxResourceFactory.SETTINGS_ICON, FxResourceFactory.BTN_IMG_SIZE);
        m_settingsButton.setId("menuTabBtn");
        m_settingsButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        m_settingsButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        m_settingsButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        m_settingsButton.setAlignment(Pos.CENTER_LEFT);
        
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
        // Only bottom padding, no left/right padding
        this.setPadding(new Insets(0, 0, PANEL_PADDING, 0));
        
        // Ensure buttons fill the width
        m_expandButton.setMaxWidth(Double.MAX_VALUE);
        m_settingsButton.setMaxWidth(Double.MAX_VALUE);
        
        // Setup width property
        availableWidth.set(DEFAULT_SMALL_WIDTH);
    }
    
    /**
     * Initialize with stage reference and register with DeferredLayoutManager.
     * This should be called by TabManagerStage after construction.
     */
    public void initializeLayout(Stage stage) {
        if (this.stage != null) {
            // Already initialized, cleanup old listeners first
            cleanupListeners();
        }
        
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
            double containerWidth = availableWidth.get() - 8; // Padding adjustment
            
            // Check if scrollbar is visible
            if (m_listScroll.getVbarPolicy() == ScrollPane.ScrollBarPolicy.AS_NEEDED) {
                double contentHeight = m_buttonContainer.getHeight();
                double viewportHeight = m_listScroll.getViewportBounds().getHeight();
                
                if (contentHeight > viewportHeight && viewportHeight > 0) {
                    containerWidth -= 15; // Typical scrollbar width
                }
            }
            
            return new LayoutData.Builder()
                .width(Math.max(0, containerWidth))
                .build();
        });
        
        // Register expand and settings buttons for proper width
        DeferredLayoutManager.register(stage, m_expandButton, _ -> {
            return new LayoutData.Builder()
                .width(availableWidth.get())
                .build();
        });
        
        DeferredLayoutManager.register(stage, m_settingsButton, _ -> {
            return new LayoutData.Builder()
                .width(availableWidth.get())
                .build();
        });
        
        // Setup listeners with null checks
        setupListeners();
    }
    
    /**
     * Setup all property listeners with proper null checking
     */
    private void setupListeners() {
        if (stage == null || stage.getScene() == null) {
            return;
        }
        
        // Scene height listener
        sceneHeightListener = (_, _, _) -> {
            if (stage != null) {
                DeferredLayoutManager.markDirty(this);
            }
        };
        stage.getScene().heightProperty().addListener(sceneHeightListener);
        
        // Button container height listener
        containerHeightListener = (_, _, _) -> {
            if (stage != null) {
                DeferredLayoutManager.markDirty(m_buttonContainer);
                debounceButtonSizes();
            }
        };
        m_buttonContainer.heightProperty().addListener(containerHeightListener);
        
        // Viewport bounds listener
        viewportBoundsListener = (_, _, _) -> {
            if (stage != null) {
                DeferredLayoutManager.markDirty(m_buttonContainer);
                debounceButtonSizes();
            }
        };
        m_listScroll.viewportBoundsProperty().addListener(viewportBoundsListener);
        
        // Listen for availableWidth changes to update button widths
        availableWidth.addListener((_, _, _) -> {
            if (stage != null) {
                DeferredLayoutManager.markDirty(m_expandButton);
                DeferredLayoutManager.markDirty(m_settingsButton);
                DeferredLayoutManager.markDirty(m_buttonContainer);
                // Update sidebar button sizes immediately during animation
                updateButtonSizes();
            }
        });
    }
    
    /**
     * Cleanup listeners to prevent memory leaks
     */
    private void cleanupListeners() {
        if (stage != null && stage.getScene() != null) {
            if (sceneHeightListener != null) {
                stage.getScene().heightProperty().removeListener(sceneHeightListener);
            }
        }
        
        if (containerHeightListener != null) {
            m_buttonContainer.heightProperty().removeListener(containerHeightListener);
        }
        
        if (viewportBoundsListener != null) {
            m_listScroll.viewportBoundsProperty().removeListener(viewportBoundsListener);
        }
    }

    private void debounceButtonSizes() {
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
    
    public BufferedButton getSettingsButton() {
        return m_settingsButton;
    }
    
    public BufferedButton getExpandButton() {
        return m_expandButton;
    }


    public void toggleExpanded() {
        double startWidth = getLayoutBounds().getWidth();
        isExpanded = !isExpanded;

        m_expandButton.setText(isExpanded ? m_title : "");
        m_expandButton.setPadding(isExpanded ? m_expandedBtnInsets : m_btnInsets);
        m_expandButton.setGraphicTextGap(isExpanded ? 15 : 0);

        m_settingsButton.setText(isExpanded ? "Settings" : "");
        m_settingsButton.setPadding(isExpanded ? m_expandedBtnInsets : m_btnInsets);
        m_settingsButton.setGraphicTextGap(isExpanded ? m_btnTextGap : 0);
   
        double endWidth = isExpanded ? DEFAULT_LARGE_WIDTH : DEFAULT_SMALL_WIDTH;

        // Cancel any running animation
        if (m_transitionTimeline != null) {
            m_transitionTimeline.stop();
        }

        // Adaptive duration - fixed calculation
        long now = System.currentTimeMillis();
        long delta = now - m_lastToggleTime;
        m_lastToggleTime = now;
        
        // Reduce duration for rapid toggles
        double adaptiveFactor = Math.min(1.0, delta / 1000.0); // Normalize delta to 0-1 range
        double baseDuration = TOGGLE_BASE_DURATION;
        double minDuration = TOGGLE_MIN_DURATION;
        double adaptiveDuration = Math.max(minDuration, 
            baseDuration * (1.0 - ADAPTIVE_DURATION_FACTOR * (1.0 - adaptiveFactor)));
        
        double remainingFraction = Math.abs(startWidth - endWidth) / (DEFAULT_LARGE_WIDTH - DEFAULT_SMALL_WIDTH);
        double durationMs = adaptiveDuration * remainingFraction;

        // Create timeline
        m_transitionTimeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(prefWidthProperty(), startWidth)),
            new KeyFrame(Duration.millis(durationMs), new KeyValue(prefWidthProperty(), endWidth, Interpolator.EASE_BOTH))
        );

        // Update width constraints during animation - throttled for performance
        final long[] lastUpdate = {0};
        final double updateInterval = 16.0; // ~60fps
        
        m_transitionTimeline.currentTimeProperty().addListener((_, _, newTime) -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdate[0] < updateInterval) {
                return; // Skip this update
            }
            lastUpdate[0] = currentTime;
            
            double progress = newTime.toMillis() / durationMs;
            double width = startWidth + (endWidth - startWidth) * progress;
            setMinWidth(width);
            setMaxWidth(width);
            availableWidth.set(width);
            
            if (stage != null) {
                DeferredLayoutManager.markDirty(this);
            }
        });

        m_transitionTimeline.setOnFinished(_ -> {
            setMinWidth(endWidth);
            setMaxWidth(endWidth);
            availableWidth.set(endWidth);
            
            if (stage != null) {
                DeferredLayoutManager.markDirty(m_buttonContainer);
                m_scrollHelper.refresh();
            }
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
    
                button.getButton().setPadding(isExpanded ? m_expandedBtnInsets : m_btnInsets);
                button.getButton().setGraphicTextGap(isExpanded ? m_btnTextGap : 0);
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
    
    public List<SideBarButton> getButtons() {
        return new ArrayList<>(m_buttons);
    }
    
    /**
     * Update all button sizes based on current container width
     */
    private void updateButtonSizes() {
        double containerWidth = m_buttonContainer.getWidth();
        if (containerWidth <= 0) {
            return; // Not yet laid out
        }
        
        double buttonWidth = containerWidth - 1;
        
        for (SideBarButton button : m_buttons) {
            if (button.getHeight() <= 0) continue;
            button.setPrefWidth(buttonWidth);
            if (!isExpanded) {
                button.setPrefHeight(DEFAULT_SMALL_WIDTH);
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
        double viewportHeight = m_listScroll.getViewportBounds().getHeight();
        if (viewportHeight > 0) {
            return m_buttonContainer.getHeight() > viewportHeight;
        }
        return false;
    }
    
    /**
     * Cleanup method to be called when the panel is no longer needed
     */
    public void dispose() {
        cleanupListeners();
        
        if (m_transitionTimeline != null) {
            m_transitionTimeline.stop();
        }
        
        if (debounce != null) {
            debounce.stop();
        }
        
        // Cancel any pending async tasks
        AtomicBoolean oldFlag = cancelFlag.getAndSet(new AtomicBoolean(false));
        oldFlag.set(true);
    }
}