package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.app.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.app.control.layout.LayoutData;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.BufferedMenuButton;
import io.netnotes.gui.fx.utils.TaskUtils;

public class SideBarPanel extends VBox {
    public final static int DEFAULT_SMALL_WIDTH = 50;
    public final static int DEFAULT_LARGE_WIDTH = 200;
    
    private final VBox buttonContainer;
    private final BufferedMenuButton settingsButton;
    private final BufferedButton expandButton;
    private final List<SideBarButton> buttons;
    private final ScrollPane listScroll;
    private final HBox listBoxPadding;

    private final AtomicReference<CompletableFuture<Void>> m_currentTask =
        new AtomicReference<>(CompletableFuture.completedFuture(null));

    private boolean isExpanded = false;
    private Stage stage; // Set by TabManagerStage
    
    public SideBarPanel() {
        this.buttons = new ArrayList<>();
        
        this.setId("appMenuBox");
        this.setPrefWidth(DEFAULT_SMALL_WIDTH);
        this.setMinWidth(DEFAULT_SMALL_WIDTH);
        this.setMaxWidth(DEFAULT_SMALL_WIDTH);
        
        // Expand/collapse button
        expandButton = new BufferedButton(FxResourceFactory.TOGGLE_FRAME, FxResourceFactory.BTN_IMG_SIZE);
        expandButton.setId("menuTabBtn");
        // Note: Don't set onAction here - TabManagerStage will handle it
        
        // Settings button
        settingsButton = new BufferedMenuButton(FxResourceFactory.SETTINGS_ICON, FxResourceFactory.BTN_IMG_SIZE);
        
        // Button container
        buttonContainer = new VBox(5);
        HBox.setHgrow(buttonContainer, Priority.ALWAYS);
        buttonContainer.setPadding(new Insets(0, 0, 2, 0));
        buttonContainer.setAlignment(Pos.TOP_LEFT);
        
        // Padding container for button list
        listBoxPadding = new HBox(buttonContainer);
        
        // Scroll content
        VBox scrollContentBox = new VBox(listBoxPadding);
        
        // Scrollable list
        listScroll = new ScrollPane(scrollContentBox);
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        this.getChildren().addAll(expandButton, listScroll, spacer, settingsButton);
        this.setPadding(new Insets(0, 0, 10, 0));
    }
    
    /**
     * Initialize with stage reference and register with DeferredLayoutManager.
     * This should be called by TabManagerStage after construction.
     */
    public void initializeLayout(Stage stage) {
        this.stage = stage;
        
        // Register the scroll pane for layout
        DeferredLayoutManager.register(stage, listScroll, ctx -> {
            if (stage.getScene() == null) {
                return new LayoutData.Builder().build();
            }
            
            double sceneHeight = stage.getScene().getHeight();
            double expandBtnHeight = expandButton.getHeight();
            double settingsBtnHeight = settingsButton.getHeight();
            double padding = this.getPadding().getTop() + this.getPadding().getBottom();
            
            // Calculate available height for scroll pane
            double availableHeight = sceneHeight - expandBtnHeight - settingsBtnHeight - padding;
            
            // Set scroll pane dimensions
            double currentWidth = isExpanded ? DEFAULT_LARGE_WIDTH : DEFAULT_SMALL_WIDTH;
            
            return new LayoutData.Builder()
                .width(currentWidth)
                .height(Math.max(100, availableHeight))
                .build();
        });
        
        // Register the padding container for width
        DeferredLayoutManager.register(stage, listBoxPadding, ctx -> {
            if (stage.getScene() == null) {
                return new LayoutData.Builder().build();
            }
            
            double sceneHeight = stage.getScene().getHeight();
            double expandBtnHeight = expandButton.getHeight();
            double settingsBtnHeight = settingsButton.getHeight();
            double padding = this.getPadding().getTop() + this.getPadding().getBottom();
            
            double availableHeight = sceneHeight - expandBtnHeight - settingsBtnHeight - padding;
            
            return new LayoutData.Builder()
                .height(Math.max(100, availableHeight))
                .build();
        });
        
        // Listen for scene height changes
        stage.getScene().heightProperty().addListener((obs, old, newVal) -> {
            DeferredLayoutManager.markDirty(listScroll);
            DeferredLayoutManager.markDirty(listBoxPadding);
        });
    }
    
    public void addButton(SideBarButton button) {
        button.setMaxWidth(Double.MAX_VALUE);
        buttons.add(button);
        buttonContainer.getChildren().add(button);
    }
    
    public void removeButton(SideBarButton button) {
        buttons.remove(button);
        buttonContainer.getChildren().remove(button);
    }
    
    public void clearButtons() {
        buttons.clear();
        buttonContainer.getChildren().clear();
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

        // Cancel any running task first
        CompletableFuture<Void> previous = m_currentTask.getAndSet(new CompletableFuture<>());
        if (previous != null && !previous.isDone()) {
            previous.cancel(true);
        }

        // Create a new chain
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (SideBarButton button : buttons) {
            chain = chain.thenComposeAsync(
                ignored -> button.updateIsExpanded(isExpanded),
                TaskUtils.getVirtualExecutor()
            );
        }

        m_currentTask.set(chain);

        // Optional: handle completion or failure cleanly
        chain.whenCompleteAsync((r, ex) -> {
            if (ex instanceof CancellationException) {
                System.out.println("Sidebar transition cancelled");
            } else if (ex != null) {
                ex.printStackTrace();
            } else {
                System.out.println("Sidebar transition complete");
            }
        }, TaskUtils.getVirtualExecutor());

        // Mark layout dirty after expansion
        if (stage != null) {
            DeferredLayoutManager.markDirty(listScroll);
        }
    }
    
    public boolean isExpanded() {
        return isExpanded;
    }
    
    public List<SideBarButton> getButtons() {
        return new ArrayList<>(buttons);
    }
}