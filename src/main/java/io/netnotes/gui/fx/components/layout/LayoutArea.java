package io.netnotes.gui.fx.components.layout;

import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import io.netnotes.gui.fx.display.control.layout.DeferredLayoutManager;
import io.netnotes.gui.fx.display.control.layout.LayoutData;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Container component that wraps BufferedLayoutArea with proper scroll bar management
 * and lifecycle handling (scene/stage detection, deferred layout registration).
 */
public class LayoutArea extends BorderPane {
    
    private final LayoutCanvas m_layoutArea;
    private final ScrollBar m_verticalScrollBar;
    private final ScrollBar m_horizontalScrollBar;
    private final StackPane m_contentPane;
    
    private Stage m_stage = null;
    
    // Scroll bar visibility
    private ScrollBarPolicy m_vbarPolicy = ScrollBarPolicy.AS_NEEDED;
    private ScrollBarPolicy m_hbarPolicy = ScrollBarPolicy.AS_NEEDED;
    
    // Scene/Stage listeners
    private ChangeListener<Scene> sceneListener;
    private ChangeListener<Window> windowListener;
    private ChangeListener<Number> widthListener;
    private ChangeListener<Number> heightListener;
    
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicBoolean isAttachedToScene = new AtomicBoolean(false);
    
    /**
     * Scroll bar visibility policies
     */
    public enum ScrollBarPolicy {
        ALWAYS,
        NEVER,
        AS_NEEDED
    }
    
    // ========== Constructors ==========
    
    public LayoutArea() {
        this(null, 600, 400);
    }
    
    public LayoutArea(HostServices hostServices) {
        this(hostServices, 600, 400);
    }
    
    public LayoutArea(int width, int height) {
        this(null, width, height);
    }
    
    public LayoutArea(HostServices hostServices, int width, int height) {
      
        // Create layout area (without internal deferred layout registration)
        if (hostServices != null) {
            m_layoutArea = new LayoutCanvas(hostServices, width, height);
        } else {
            m_layoutArea = new LayoutCanvas(width, height);
        }
        
        // Create scroll bars
        m_verticalScrollBar = new ScrollBar();
        m_verticalScrollBar.setOrientation(Orientation.VERTICAL);
        m_verticalScrollBar.setMin(0);
        m_verticalScrollBar.setMax(0);
        m_verticalScrollBar.setValue(0);
        m_verticalScrollBar.setVisible(false);
        
        m_horizontalScrollBar = new ScrollBar();
        m_horizontalScrollBar.setOrientation(Orientation.HORIZONTAL);
        m_horizontalScrollBar.setMin(0);
        m_horizontalScrollBar.setMax(0);
        m_horizontalScrollBar.setValue(0);
        m_horizontalScrollBar.setVisible(false);
        
        // Content pane for layout area
        m_contentPane = new StackPane(m_layoutArea);
        m_contentPane.setAlignment(Pos.TOP_LEFT);
        
        // Layout in BorderPane
        setCenter(m_contentPane);
        
        // Setup scroll bar listeners
        setupScrollBarListeners();
        
        // Setup scene/stage detection
        setupSceneDetection();
        
        // Setup size listeners
        setupSizeListeners();
    }
    
    // ========== Lifecycle Management ==========
    
    private void setupSceneDetection() {

        sceneListener = (_, oldScene, newScene) -> {
            if (oldScene != null) {
                // Detached from old scene
                handleSceneDetached();
            }
            
            if (newScene != null) {
                // Attached to new scene
                handleSceneAttached(newScene);
            }
        };
        sceneProperty().addListener(sceneListener);
        
        // Check initial scene
        if (getScene() != null) {
            handleSceneAttached(getScene());
        }
    }
    
    private void handleSceneAttached(Scene scene) {
        isAttachedToScene.set(true);
        
        // Watch for window/stage changes
        windowListener = (_, oldWindow, newWindow) -> {
            if (oldWindow instanceof Stage) {
                handleStageDetached();
            }
            
            if (newWindow instanceof Stage) {
                m_stage = (Stage) newWindow;
                handleStageAttached();
            }
        };
        scene.windowProperty().addListener(windowListener);
        
        // Check current window
        if (scene.getWindow() instanceof Stage) {
            m_stage = (Stage) scene.getWindow();
            handleStageAttached();
        }
    }
    
    private void handleSceneDetached() {
        isAttachedToScene.set(false);
        handleStageDetached();
        
        if (getScene() != null && windowListener != null) {
            getScene().windowProperty().removeListener(windowListener);
            windowListener = null;
        }
    }
    
    private void handleStageAttached() {
        if (m_stage != null && isAttachedToScene.get() && !isRegistered.get()) {
            registerLayoutAreaWithDeferredLayout();
        }
    }
    
    private void handleStageDetached() {
        unregisterFromDeferredLayout();
    }
    
    private void registerLayoutAreaWithDeferredLayout() {
        if (m_stage != null && isRegistered.compareAndSet(false, true)) {

            DeferredLayoutManager.register(m_stage, m_layoutArea, _ -> {
                if (m_stage.getScene() == null) {
                    return new LayoutData.Builder().build();
                }
        
                // Return sizing information for the container
                // This allows the container to resize with the stage/parent
                double width = calculateCanvasWidth();
                double height = calculateCanvasHeight();
                
                // If we have valid dimensions, return them
                if (width > 0 && height > 0) {
                    return new LayoutData.Builder()
                        .width(width)
                        .height(height)
                        .build();
                }

                return new LayoutData.Builder().build();
            },()->{
                m_layoutArea.applyLayout();
                
                // Update scroll bars after layout
                updateScrollBars();
            });
        }
    }

    private double calculateCanvasWidth(){
        double width = getLayoutBounds().getWidth() - getInsets().getLeft() - getInsets().getRight();
        
        return Math.max(0, width - (showVScroll ? m_verticalScrollBar.getLayoutBounds().getWidth() : 0));
    }

    private double calculateCanvasHeight(){
        double height = getLayoutBounds().getHeight() - getInsets().getTop() - getInsets().getBottom();
        
        return height - (showVScroll ? m_horizontalScrollBar.getLayoutBounds().getHeight() : 0);
    }

   
    
    private void unregisterFromDeferredLayout() {
        if (isRegistered.compareAndSet(true, false)) {
            DeferredLayoutManager.unregister(this);
        }
    }
    
    // ========== Scroll Bar Management ==========
    
    private void setupScrollBarListeners() {
        // Vertical scroll
        m_verticalScrollBar.valueProperty().addListener((_, _, newVal) -> {
            if (newVal != null) {
                m_layoutArea.setScrollY(newVal.intValue(), true);
            }
        });
        
        // Horizontal scroll
        m_horizontalScrollBar.valueProperty().addListener((_, _, newVal) -> {
            if (newVal != null) {
                m_layoutArea.setScrollX(newVal.intValue(), true);
            }
        });
        
        // Update scroll bars when layout area scrolls (e.g., cursor navigation)
        // This is a simple polling approach; could be improved with property binding
    }
    private boolean showVScroll = false;
    private boolean showHScroll = false;

    private void updateScrollBars() {
        // Get scroll bounds from layout area
        int maxScrollX = m_layoutArea.getMaxScrollX();
        int maxScrollY = m_layoutArea.getMaxScrollY();
        
        int viewportWidth = m_layoutArea.getPreferredWidth();
        int viewportHeight = m_layoutArea.getPreferredHeight();
        
        // Update vertical scroll bar
        boolean needsVScroll = maxScrollY > 0;
        showVScroll = shouldShowScrollBar(m_vbarPolicy, needsVScroll);
        
        if (showVScroll) {
            m_verticalScrollBar.setMax(maxScrollY);
            m_verticalScrollBar.setVisibleAmount(viewportHeight * 0.1); // 10% of viewport
            m_verticalScrollBar.setBlockIncrement(viewportHeight * 0.9); // 90% of viewport
            m_verticalScrollBar.setUnitIncrement(20);
            
            // Sync with layout area scroll position
            int currentScrollY = m_layoutArea.getScrollY();
            if (Math.abs(m_verticalScrollBar.getValue() - currentScrollY) > 1) {
                m_verticalScrollBar.setValue(currentScrollY);
            }
        }
        
        if (m_verticalScrollBar.isVisible() != showVScroll) {
            m_verticalScrollBar.setVisible(showVScroll);
            if (showVScroll) {
                setRight(m_verticalScrollBar);
            } else {
                setRight(null);
            }
        }
        
        // Update horizontal scroll bar
        boolean needsHScroll = maxScrollX > 0;
        showHScroll = shouldShowScrollBar(m_hbarPolicy, needsHScroll);
        
        if (showHScroll) {
            m_horizontalScrollBar.setMax(maxScrollX);
            m_horizontalScrollBar.setVisibleAmount(viewportWidth * 0.1);
            m_horizontalScrollBar.setBlockIncrement(viewportWidth * 0.9);
            m_horizontalScrollBar.setUnitIncrement(20);
            
            // Sync with layout area scroll position
            int currentScrollX = m_layoutArea.getScrollX();
            if (Math.abs(m_horizontalScrollBar.getValue() - currentScrollX) > 1) {
                m_horizontalScrollBar.setValue(currentScrollX);
            }
        }
        
        if (m_horizontalScrollBar.isVisible() != showHScroll) {
            m_horizontalScrollBar.setVisible(showHScroll);
            if (showHScroll) {
                setBottom(m_horizontalScrollBar);
            } else {
                setBottom(null);
            }
        }
    }
    
    private boolean shouldShowScrollBar(ScrollBarPolicy policy, boolean needed) {
        switch (policy) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case AS_NEEDED:
                return needed;
            default:
                return false;
        }
    }
    
    // ========== Size Management ==========
    
    private void setupSizeListeners() {
        widthListener = (_, _, _) -> {
            if (isAttachedToScene.get()) {
                DeferredLayoutManager.markDirty(this);
            }
        };
        widthProperty().addListener(widthListener);
        
        heightListener = (_, _, _) -> {
            if (isAttachedToScene.get()) {
                DeferredLayoutManager.markDirty(this);
            }
        };
        heightProperty().addListener(heightListener);
    }
    
    // ========== Scroll Bar Policy ==========
    
    public void setVBarPolicy(ScrollBarPolicy policy) {
        m_vbarPolicy = policy;
        updateScrollBars();
    }
    
    public ScrollBarPolicy getVBarPolicy() {
        return m_vbarPolicy;
    }
    
    public void setHBarPolicy(ScrollBarPolicy policy) {
        m_hbarPolicy = policy;
        updateScrollBars();
    }
    
    public ScrollBarPolicy getHBarPolicy() {
        return m_hbarPolicy;
    }
    
    // ========== Layout Area Access ==========
    
    public LayoutCanvas getLayoutCanvas() {
        return m_layoutArea;
    }
    
    public ScrollBar getVerticalScrollBar() {
        return m_verticalScrollBar;
    }
    
    public ScrollBar getHorizontalScrollBar() {
        return m_horizontalScrollBar;
    }
    
    // ========== Stage Management ==========
    
    public void setStage(Stage stage) {
        if (m_stage != stage) {
            handleStageDetached();
            m_stage = stage;
            if (isAttachedToScene.get()) {
                handleStageAttached();
            }
        }
    }
    
    public Stage getStage() {
        return m_stage;
    }
    
    // ========== Cleanup ==========
    
    public void shutdown() {
        unregisterFromDeferredLayout();
        
        if (sceneListener != null) {
            sceneProperty().removeListener(sceneListener);
            sceneListener = null;
        }
        
        if (windowListener != null && getScene() != null) {
            getScene().windowProperty().removeListener(windowListener);
            windowListener = null;
        }
        
        if (widthListener != null) {
            widthProperty().removeListener(widthListener);
            widthListener = null;
        }
        
        if (heightListener != null) {
            heightProperty().removeListener(heightListener);
            heightListener = null;
        }
        
        m_layoutArea.shutdown();
    }
}