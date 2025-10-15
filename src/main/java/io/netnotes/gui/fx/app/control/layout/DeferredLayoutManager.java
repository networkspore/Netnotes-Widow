package io.netnotes.gui.fx.app.control.layout;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;

import io.netnotes.gui.fx.app.control.FrameRateMonitor;
import io.netnotes.gui.fx.utils.TaskUtils;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Main deferred layout manager with adaptive rate monitoring
 */
public class DeferredLayoutManager {
    private static final DeferredLayoutManager INSTANCE = new DeferredLayoutManager();
    
    private final Map<Stage, StageNode> stageNodes = new LinkedHashMap<>();
    private final Map<Node, LayoutNode> nodeRegistry = new HashMap<>();
    private final Set<LayoutNode> dirtyNodes = new LinkedHashSet<>();
    private final Set<StageNode> dirtyStages = new LinkedHashSet<>();
    

    private Future<?> scheduledLayout = null;
    private long layoutDelay = TaskUtils.DEFAULT_FX_DELAY;
    private long lastLayoutTime = 0;
    private int rapidEventCount = 0;
    private final Object scheduleLock = new Object();
    
    private boolean useAdaptiveDelay = true;
    
    private DeferredLayoutManager() {}
    
    public static DeferredLayoutManager getInstance() {
        return INSTANCE;
    }
    
    // ==================== Configuration ====================
    
    public static void setLayoutDelay(long delayMillis) {
        INSTANCE.layoutDelay = delayMillis;
    }
    
    public static void setAdaptiveDelay(boolean adaptive) {
        INSTANCE.useAdaptiveDelay = adaptive;
    }
    
    // ==================== Stage Registration ====================
    
    public static void registerStage(Stage stage, StageLayoutCallback callback) {
        INSTANCE.registerStageInternal(stage, callback);
    }
    
    public static void registerStage(Stage stage, StageLayoutCallback callback, Stage... dependencies) {
        INSTANCE.registerStageInternal(stage, callback, dependencies);
    }
    
    private void registerStageInternal(Stage stage, StageLayoutCallback callback, Stage... dependencies) {
        StageNode stageNode = new StageNode(stage, callback);
        stageNodes.put(stage, stageNode);
        
        // Add dependencies
        for (Stage dep : dependencies) {
            StageNode depNode = stageNodes.get(dep);
            if (depNode != null) {
                stageNode.addDependency(depNode);
            }
        }
        
        // Auto-cleanup when stage closes
        stage.setOnHiding(e -> {
            stageNodes.remove(stage);
            // Clean up any nodes registered to this stage
            nodeRegistry.entrySet().removeIf(entry -> entry.getValue().getStage() == stage);
        });
    }
    
    // ==================== Node Registration ====================
    
    public static void register(Stage stage, Node node, LayoutCallback callback) {
        INSTANCE.registerNode(stage, node, callback);
    }
    
    private void registerNode(Stage stage, Node node, LayoutCallback callback) {
        LayoutNode layoutNode = new LayoutNode(node, stage, callback);
        nodeRegistry.put(node, layoutNode);
        
        // Build parent-child relationships by walking up the scene graph
        Node parentNode = node.getParent();
        while (parentNode != null) {
            LayoutNode parentLayoutNode = nodeRegistry.get(parentNode);
            if (parentLayoutNode != null) {
                // Found a registered parent, link them
                parentLayoutNode.addChild(layoutNode);
                break;
            }
            // Keep looking up the tree for a registered parent
            parentNode = parentNode.getParent();
        }
    }
    
    // ==================== Dirty Marking ====================
    
    public static void markDirty(Node node) {
        INSTANCE.markNodeDirty(node);
    }
    
    public static void markStageDirty(Stage stage) {
        INSTANCE.markStageNodeDirty(stage);
    }
    
    private void markNodeDirty(Node node) {
        LayoutNode layoutNode = nodeRegistry.get(node);
        if (layoutNode != null) {
            dirtyNodes.add(layoutNode);
            scheduleLayout();
        }
    }
    
    private void markStageNodeDirty(Stage stage) {
        StageNode stageNode = stageNodes.get(stage);
        if (stageNode != null) {
            dirtyStages.add(stageNode);
            scheduleLayout();
        }
    }
    
    // ==================== Scheduling ====================
    
    private void scheduleLayout() {
        synchronized (scheduleLock) {
            long now = System.currentTimeMillis();
            long timeSinceLastLayout = now - lastLayoutTime;
            
            // Track rapid event bursts for additional adaptive behavior
            if (timeSinceLastLayout < 100) {
                rapidEventCount++;
            } else {
                rapidEventCount = 0;
            }
            
            // Cancel any pending layout
            if (scheduledLayout != null && !scheduledLayout.isDone()) {
                scheduledLayout.cancel(false);
            }
            
            // Schedule new layout - delay is automatically adaptive via TaskUtils
            if (useAdaptiveDelay) {
                scheduledLayout = TaskUtils.fxDelay(event -> performLayout());
            } else {
            
                if (timeSinceLastLayout < 100) {
                    rapidEventCount++;
                    if (rapidEventCount > 5) {
                        // Heavy resizing detected, use frame rate monitor
                        layoutDelay = FrameRateMonitor.getInstance().getRecommendedDebounceDelay();
                    }
                } else {
                    rapidEventCount = 0;
                    layoutDelay = TaskUtils.DEFAULT_FX_DELAY; // default when not under pressure
                }
                
                scheduledLayout = TaskUtils.fxDelay(layoutDelay, event -> performLayout());
            }
        }
    }
    
    // ==================== Layout Execution ====================
    
    private void performLayout() {
        long startTime = System.nanoTime();
        
        synchronized (scheduleLock) {
            scheduledLayout = null;
            lastLayoutTime = System.currentTimeMillis();
        }
        
        // PHASE 1: Position stages relative to each other
        if (!dirtyStages.isEmpty()) {
            List<StageNode> sortedStages = topologicalSortStages(new ArrayList<>(dirtyStages));
            StageContext context = new StageContext(stageNodes, null);
            
            for (StageNode stageNode : sortedStages) {
                stageNode.calculate(context);
            }
            for (StageNode stageNode : sortedStages) {
                stageNode.apply();
            }
            dirtyStages.clear();
        }
        
        // PHASE 2: Layout nodes within each stage
        if (!dirtyNodes.isEmpty()) {
            Map<Stage, List<LayoutNode>> nodesByStage = groupByStage(dirtyNodes);
            
            for (Map.Entry<Stage, List<LayoutNode>> entry : nodesByStage.entrySet()) {
                List<LayoutNode> sorted = topologicalSortNodes(entry.getValue());
                
                for (LayoutNode node : sorted) {
                    node.calculate();
                }
                for (LayoutNode node : sorted) {
                    node.apply();
                }
            }
            dirtyNodes.clear();
        }
        
        // Record frame time and layout cost for adaptive performance
        long endTime = System.nanoTime();
        double layoutTimeMs = (endTime - startTime) / 1_000_000.0;
        FrameRateMonitor.getInstance().recordFrame(layoutTimeMs);
    }
    
    // ==================== Topological Sorting ====================
    
    private List<StageNode> topologicalSortStages(List<StageNode> stages) {
        // Kahn's algorithm for topological sort
        Map<StageNode, Integer> inDegree = new HashMap<>();
        for (StageNode node : stages) {
            inDegree.put(node, 0);
        }
        
        // Calculate in-degrees
        for (StageNode node : stages) {
            for (StageNode dep : node.getDependencies()) {
                if (inDegree.containsKey(dep)) {
                    inDegree.put(node, inDegree.get(node) + 1);
                }
            }
        }
        
        // Queue nodes with no dependencies
        Queue<StageNode> queue = new LinkedList<>();
        for (Map.Entry<StageNode, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<StageNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            StageNode current = queue.poll();
            sorted.add(current);
            
            // Reduce in-degree for dependent nodes
            for (StageNode node : stages) {
                if (node.getDependencies().contains(current)) {
                    int degree = inDegree.get(node) - 1;
                    inDegree.put(node, degree);
                    if (degree == 0) {
                        queue.offer(node);
                    }
                }
            }
        }
        
        return sorted;
    }
    
    private List<LayoutNode> topologicalSortNodes(List<LayoutNode> nodes) {
        // Sort by depth in scene graph (parents before children)
        nodes.sort(Comparator.comparingInt(LayoutNode::getDepth));
        return nodes;
    }
    
    private Map<Stage, List<LayoutNode>> groupByStage(Set<LayoutNode> nodes) {
        Map<Stage, List<LayoutNode>> grouped = new HashMap<>();
        for (LayoutNode node : nodes) {
            grouped.computeIfAbsent(node.getStage(), k -> new ArrayList<>())
                   .add(node);
        }
        return grouped;
    }
    
    // ==================== Diagnostics ====================
    
    public static void printDiagnostics() {
        FrameRateMonitor monitor = FrameRateMonitor.getInstance();
        System.out.println("=== DeferredLayoutManager Diagnostics ===");
        System.out.printf("Average Frame Time: %.2f ms%n", monitor.getAverageFrameTime());
        System.out.printf("Average Layout Cost: %.2f ms%n", monitor.getAverageLayoutCost());
        System.out.printf("Layout Percentage: %.1f%%%n", monitor.getLayoutPercentage());
        System.out.printf("Current FPS: %.2f%n", monitor.getFPS());
        System.out.printf("Under Pressure: %s%n", monitor.isUnderPressure());
        System.out.printf("Layout Bottleneck: %s%n", monitor.isLayoutBottleneck());
        System.out.printf("Recommended Delay: %d ms%n", monitor.getRecommendedDebounceDelay());
        System.out.printf("Registered Stages: %d%n", INSTANCE.stageNodes.size());
        System.out.printf("Registered Nodes: %d%n", INSTANCE.nodeRegistry.size());
    }
}