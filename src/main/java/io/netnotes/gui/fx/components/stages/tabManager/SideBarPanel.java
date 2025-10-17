package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.DoubleProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.BufferedMenuButton;
import io.netnotes.gui.fx.display.FxResourceFactory;
import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;
import io.netnotes.gui.fx.display.control.layout.ScrollPaneHelper;
import io.netnotes.gui.fx.utils.TaskUtils;

public class SideBarPanel extends VBox {
    public final static int DEFAULT_SMALL_WIDTH = 50;
    public final static int DEFAULT_LARGE_WIDTH = 200;
    public final static int BUTTON_SPACING = 5;
    public final static int PANEL_PADDING = 10;
    
    private final VBox buttonContainer;
    private final BufferedMenuButton settingsButton;
    private final BufferedButton expandButton;
    private final List<SideBarButton> buttons;
    private final ScrollPane listScroll;
    private final HBox listBoxPadding;
    
    private ScrollPaneHelper scrollHelper;
    private CompletableFuture<Void> m_currentTask = CompletableFuture.completedFuture(null);

    private boolean isExpanded = false;
    private Stage stage;
    
    // Properties for dynamic sizing
    private final DoubleProperty availableWidth = new SimpleDoubleProperty();
    private final DoubleProperty availableHeight = new SimpleDoubleProperty();
    
    public SideBarPanel() {
        this.buttons = new ArrayList<>();
        
        this.setId("appMenuBox");
        this.setPrefWidth(DEFAULT_SMALL_WIDTH);
        this.setMinWidth(DEFAULT_SMALL_WIDTH);
        this.setMaxWidth(DEFAULT_SMALL_WIDTH);
        
        // Expand/collapse button
        expandButton = new BufferedButton(FxResourceFactory.TOGGLE_FRAME, FxResourceFactory.BTN_IMG_SIZE);
        expandButton.setId("menuTabBtn");
        expandButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        expandButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        expandButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        
        // Settings button
        settingsButton = new BufferedMenuButton(FxResourceFactory.SETTINGS_ICON, FxResourceFactory.BTN_IMG_SIZE);
        settingsButton.setPrefHeight(DEFAULT_SMALL_WIDTH);
        settingsButton.setMinHeight(DEFAULT_SMALL_WIDTH);
        settingsButton.setMaxHeight(DEFAULT_SMALL_WIDTH);
        
        // Button container
        buttonContainer = new VBox(BUTTON_SPACING);
        HBox.setHgrow(buttonContainer, Priority.ALWAYS);
        buttonContainer.setPadding(new Insets(0, 0, 2, 0));
        buttonContainer.setAlignment(Pos.TOP_LEFT);
        
        // Padding container for button list
        listBoxPadding = new HBox(buttonContainer);
        listBoxPadding.setPadding(new Insets(2));
        
        // Scroll content
        VBox scrollContentBox = new VBox(listBoxPadding);
        
        // Scrollable list
        listScroll = new ScrollPane(scrollContentBox);
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        this.getChildren().addAll(expandButton, listScroll, spacer, settingsButton);
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
        DeferredLayoutManager.register(stage, this, ctx -> {
            if (stage.getScene() == null) {
                return new LayoutData.Builder().build();
            }
            
            double sceneHeight = stage.getScene().getHeight();
            double topBarHeight = 35; // TabTopBar height
            double sidebarHeight = sceneHeight - topBarHeight;
            
            // Update available dimensions
            availableHeight.set(sidebarHeight);
            
            return new LayoutData.Builder()
                .height(sidebarHeight)
                .build();
        });
        
        // Create ScrollPaneHelper for proper scroll pane sizing
        DoubleExpression[] heightOffsets = {
            expandButton.heightProperty(),
            settingsButton.heightProperty(),
            new SimpleDoubleProperty(PANEL_PADDING + BUTTON_SPACING) // Padding and spacing
        };
        
        scrollHelper = new ScrollPaneHelper(
            stage,
            listScroll,
            listBoxPadding,
            availableWidth,
            availableHeight,
            new DoubleProperty[] { new SimpleDoubleProperty(4) }, // Width offset for padding
            heightOffsets
        );
        
        // Register button container to adapt to scroll pane content width
        DeferredLayoutManager.register(stage, buttonContainer, ctx -> {
            // Calculate available width for buttons
            // Account for scrollbar when visible
            double containerWidth = availableWidth.get() - 8; // Padding adjustment
            
            // Check if scrollbar is visible
            if (listScroll.getVbarPolicy() == ScrollPane.ScrollBarPolicy.AS_NEEDED) {
                // If content height exceeds viewport, scrollbar will appear
                double contentHeight = buttonContainer.getHeight();
                double viewportHeight = listScroll.getViewportBounds().getHeight();
                
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
        stage.getScene().heightProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(this);
        });
        
        // Listen for button container height changes (for scrollbar detection)
        buttonContainer.heightProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(buttonContainer);
            // Also update all buttons when container size changes
            updateButtonSizes();
        });
        
        // Listen for scroll pane viewport changes
        listScroll.viewportBoundsProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(buttonContainer);
            updateButtonSizes();
        });
    }
    
    public void addButton(SideBarButton button) {
        button.setMaxWidth(Double.MAX_VALUE);
        buttons.add(button);
        buttonContainer.getChildren().add(button);
        button.updateIsExpanded(isExpanded);
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(buttonContainer);
        }
    }
    
    public void removeButton(SideBarButton button) {
        buttons.remove(button);
        buttonContainer.getChildren().remove(button);
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(buttonContainer);
        }
    }
    
    public void clearButtons() {
        buttons.clear();
        buttonContainer.getChildren().clear();
        
        // Mark container dirty to recalculate scrollbar needs
        if (stage != null) {
            DeferredLayoutManager.markDirty(buttonContainer);
        }
    }
    
    public BufferedMenuButton getSettingsButton() {
        return settingsButton;
    }
    
    public BufferedButton getExpandButton() {
        return expandButton;
    }
    
    public void toggleExpanded() {
        isExpanded = !isExpanded;

        double width = isExpanded ? DEFAULT_LARGE_WIDTH : DEFAULT_SMALL_WIDTH;
        this.setPrefWidth(width);
        this.setMinWidth(width);
        this.setMaxWidth(width);
        
        // Update available width for scroll pane helper
        availableWidth.set(width);

        // Cancel any running task first
        if(m_currentTask != null && !m_currentTask.isDone()){
            m_currentTask.cancel(true);
        }

        // Create a new chain to update buttons
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (SideBarButton button : buttons) {
            chain = chain.thenComposeAsync(
                ignored -> button.updateIsExpanded(isExpanded),
                TaskUtils.getVirtualExecutor()
            );
        }

        m_currentTask = chain;

        // Optional: handle completion or failure cleanly
        chain.whenCompleteAsync((r, ex) -> {
            if (ex instanceof CancellationException) {
                System.out.println("Sidebar transition cancelled");
            } else if (ex != null) {
                ex.printStackTrace();
            } else {
                System.out.println("Sidebar transition complete");
                // After expansion completes, update layout
                TaskUtils.fxDelay(e -> {
                    if (stage != null) {
                        DeferredLayoutManager.markDirty(buttonContainer);
                        scrollHelper.refresh();
                    }
                });
            }
        }, TaskUtils.getVirtualExecutor());

        // Mark layout dirty after expansion starts
        if (stage != null) {
            DeferredLayoutManager.markDirty(this);
            scrollHelper.refresh();
        }
    }

    public boolean isExpanded() {
        return isExpanded;
    }
    
    public List<SideBarButton> getButtons() {
        return new ArrayList<>(buttons);
    }
    
    /**
     * Update all button sizes based on current container width
     */
    private void updateButtonSizes() {
        if (buttonContainer.getWidth() > 0) {
            double buttonWidth = isExpanded ? 
                buttonContainer.getWidth() : 
                DEFAULT_SMALL_WIDTH - 5;
            
            for (SideBarButton button : buttons) {
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
        return buttonContainer.getWidth();
    }
    
    /**
     * Check if scrollbar is currently visible
     */
    public boolean isScrollBarVisible() {
        if (listScroll.getViewportBounds().getHeight() > 0) {
            return buttonContainer.getHeight() > listScroll.getViewportBounds().getHeight();
        }
        return false;
    }
}