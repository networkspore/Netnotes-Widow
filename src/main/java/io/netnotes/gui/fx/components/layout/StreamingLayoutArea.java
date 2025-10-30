package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.*;
import javafx.application.HostServices;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Streaming-enabled wrapper around LayoutArea/LayoutCanvas.
 * 
 * This extends LayoutArea and adds streaming protocol support by
 * setting up event consumers on the underlying LayoutCanvas.
 * 
 * This is the CLIENT side that receives commands and sends events.
 */
public class StreamingLayoutArea extends LayoutArea {
    
    // ========== ID Tracking ==========
    
    private final Map<String, LayoutSegment> m_segmentIdMap;
    private final Map<LayoutSegment, String> m_reverseIdMap;
    
    // ========== Streaming ==========
    
    private Consumer<NoteBytesObject> m_outboundStream; // Events to VirtualLayoutArea
    private boolean m_eventsEnabled = false;
    
    // ========== Constructors ==========
    
    public StreamingLayoutArea() {
        super();
        m_segmentIdMap = new ConcurrentHashMap<>();
        m_reverseIdMap = new ConcurrentHashMap<>();
        setupEventConsumers();
    }
    
    public StreamingLayoutArea(HostServices hostServices) {
        super(hostServices);
        m_segmentIdMap = new ConcurrentHashMap<>();
        m_reverseIdMap = new ConcurrentHashMap<>();
        setupEventConsumers();
    }
    
    public StreamingLayoutArea(int width, int height) {
        super(width, height);
        m_segmentIdMap = new ConcurrentHashMap<>();
        m_reverseIdMap = new ConcurrentHashMap<>();
        setupEventConsumers();
    }
    
    public StreamingLayoutArea(HostServices hostServices, int width, int height) {
        super(hostServices, width, height);
        m_segmentIdMap = new ConcurrentHashMap<>();
        m_reverseIdMap = new ConcurrentHashMap<>();
        setupEventConsumers();
    }
    
    // ========== Stream Connection ==========
    
    /**
     * Connect the outbound event stream (sends events to VirtualLayoutArea)
     */
    public void connectOutboundStream(Consumer<NoteBytesObject> stream) {
        m_outboundStream = stream;
    }
    
    /**
     * Enable/disable event forwarding
     */
    public void setEventsEnabled(boolean enabled) {
        m_eventsEnabled = enabled;
    }
    
    /**
     * Process inbound command from VirtualLayoutArea
     * This is the main entry point for remote control
     */
    public void processInboundCommand(NoteBytesObject commandData) {
        // Must run on JavaFX thread since we're modifying UI
        Platform.runLater(() -> {
            try {
                handleCommand(commandData);
            } catch (Exception e) {
                System.err.println("Error processing command: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    // ========== Event Consumer Setup ==========
    
    /**
     * Set up event consumers on the underlying LayoutCanvas.
     * This is where we hook into canvas events without extending it.
     */
    private void setupEventConsumers() {
        LayoutCanvas canvas = getLayoutCanvas();
        
        // Hook into cursor movement
        canvas.setOnCursorMove(event -> {
            notifyCursorMoved(event.segment, event.localOffset);
        });
        
        // Hook into text changes
        canvas.setOnTextChange(event -> {
            notifyTextChanged(event.segment, event.newText);
        });
        
        // Hook into segment clicks
        canvas.setOnSegmentClick(event -> {
            notifySegmentClicked(event.segment);
        });
        
        // Hook into selection changes
        canvas.setOnSelectionChange(event -> {
            notifySelectionChanged(event.startOffset, event.endOffset);
        });
        
        // Hook into layout completion
        canvas.setOnLayoutComplete(_ -> {
            sendEvent(LayoutEvent.layoutComplete());
        });
    }
    
    // ========== Command Handling ==========
    
    private void handleCommand(NoteBytesObject commandData) {
        NoteBytes typeNb = commandData.get("type") != null ? 
            commandData.get("type").getValue() : null;
        if (typeNb == null) return;
        
        int typeValue = typeNb.getAsInt();
        
        switch (typeValue) {
            case 0: // FULL_SYNC
                handleFullSync(commandData);
                break;
                
            case 1: // UPDATE
                handleUpdate(commandData);
                break;
                
            case 2: // REMOVE
                handleRemove(commandData);
                break;
                
            case 3: // INSERT
                handleInsert(commandData);
                break;
                
            case 4: // MOVE
                handleMove(commandData);
                break;
                
            default:
                System.err.println("Unknown command type: " + typeValue);
        }
    }
    
    private void handleFullSync(NoteBytesObject commandData) {
        NoteBytes segmentsNb = commandData.get("segments") != null ? 
            commandData.get("segments").getValue() : null;
        
        if (!(segmentsNb instanceof NoteBytesArray)) {
            return;
        }
        
        NoteBytesArray segments = (NoteBytesArray) segmentsNb;
        
        // Clear existing
        LayoutCanvas canvas = getLayoutCanvas();
        canvas.clear();
        m_segmentIdMap.clear();
        m_reverseIdMap.clear();
        
        // Set new segments
        canvas.setSegments(segments, false);
        
        // Send layout complete event
        sendEvent(LayoutEvent.layoutComplete());
    }
    
    private void handleUpdate(NoteBytesObject commandData) {
        NoteBytes idNb = commandData.get("segmentId") != null ? 
            commandData.get("segmentId").getValue() : null;
        NoteBytes dataNb = commandData.get("segmentData") != null ? 
            commandData.get("segmentData").getValue() : null;
        
        if (idNb == null || !(dataNb instanceof NoteBytesObject)) {
            return;
        }
        
        String segmentId = idNb.getAsString();
        NoteBytesObject segmentData = (NoteBytesObject) dataNb;
        
        LayoutSegment existingSegment = m_segmentIdMap.get(segmentId);
        LayoutCanvas canvas = getLayoutCanvas();
        
        if (existingSegment != null) {
            // Update existing segment
            updateSegmentFromData(existingSegment, segmentData);
            canvas.invalidateLayout();
        } else {
            // New segment - add it
            LayoutSegment newSegment = new LayoutSegment(segmentData);
            registerSegment(segmentId, newSegment);
            canvas.addSegment(newSegment);
        }
    }
    
    private void handleRemove(NoteBytesObject commandData) {
        NoteBytes idNb = commandData.get("segmentId") != null ? 
            commandData.get("segmentId").getValue() : null;
        
        if (idNb == null) return;
        
        String segmentId = idNb.getAsString();
        LayoutSegment segment = m_segmentIdMap.get(segmentId);
        LayoutCanvas canvas = getLayoutCanvas();
        
        if (segment != null) {
            // Find and remove from segments array
            NoteBytesArray segments = canvas.getSegments();
            for (int i = 0; i < segments.size(); i++) {
                NoteBytes item = segments.get(i);
                if (item instanceof NoteBytesObject) {
                    LayoutSegment seg = new LayoutSegment((NoteBytesObject) item);
                    if (seg == segment) {
                        canvas.removeSegment(i);
                        break;
                    }
                }
            }
            
            unregisterSegment(segmentId);
        }
    }
    
    private void handleInsert(NoteBytesObject commandData) {
        NoteBytes idNb = commandData.get("segmentId") != null ? 
            commandData.get("segmentId").getValue() : null;
        NoteBytes dataNb = commandData.get("segmentData") != null ? 
            commandData.get("segmentData").getValue() : null;
        NoteBytes indexNb = commandData.get("index") != null ? 
            commandData.get("index").getValue() : null;
        
        if (idNb == null || !(dataNb instanceof NoteBytesObject)) {
            return;
        }
        
        String segmentId = idNb.getAsString();
        NoteBytesObject segmentData = (NoteBytesObject) dataNb;
        int index = indexNb != null ? indexNb.getAsInt() : 0;
        
        LayoutSegment segment = new LayoutSegment(segmentData);
        registerSegment(segmentId, segment);
        
        LayoutCanvas canvas = getLayoutCanvas();
        canvas.addSegment(index, segment);
    }
    
    private void handleMove(NoteBytesObject commandData) {
        // TODO: Implement segment reordering
    }
    
    // ========== ID Management ==========
    
    private void registerSegment(String id, LayoutSegment segment) {
        m_segmentIdMap.put(id, segment);
        m_reverseIdMap.put(segment, id);
    }
    
    private void unregisterSegment(String id) {
        LayoutSegment segment = m_segmentIdMap.remove(id);
        if (segment != null) {
            m_reverseIdMap.remove(segment);
        }
    }
    
    private String getSegmentId(LayoutSegment segment) {
        return m_reverseIdMap.get(segment);
    }
    
    public LayoutSegment getSegmentById(String id) {
        return m_segmentIdMap.get(id);
    }
    
    // ========== Event Forwarding ==========
    
    /**
     * Send event to VirtualLayoutArea
     */
    private void sendEvent(LayoutEvent event) {
        if (m_outboundStream != null && m_eventsEnabled) {
            m_outboundStream.accept(event.toNoteBytes());
        }
    }
    
    /**
     * Public methods for LayoutCanvas to call via consumers
     */
    
    public void notifyCursorMoved(LayoutSegment segment, int localOffset) {
        if (!m_eventsEnabled) return;
        
        String segmentId = getSegmentId(segment);
        if (segmentId != null) {
            sendEvent(LayoutEvent.cursorMoved(segmentId, localOffset));
        }
    }
    
    public void notifyTextChanged(LayoutSegment segment, String newText) {
        if (!m_eventsEnabled) return;
        
        String segmentId = getSegmentId(segment);
        if (segmentId != null) {
            sendEvent(LayoutEvent.textChanged(segmentId, newText));
        }
    }
    
    public void notifySegmentClicked(LayoutSegment segment) {
        if (!m_eventsEnabled) return;
        
        String segmentId = getSegmentId(segment);
        if (segmentId != null) {
            sendEvent(LayoutEvent.segmentClicked(segmentId));
        }
    }
    
    public void notifySelectionChanged(int startOffset, int endOffset) {
        if (!m_eventsEnabled) return;
        
        LayoutEvent event = new LayoutEvent();
        event.type = LayoutEvent.EventType.SELECTION_CHANGED;
        event.data = new java.util.HashMap<>();
        event.data.put("start", new NoteInteger(startOffset));
        event.data.put("end", new NoteInteger(endOffset));
        
        sendEvent(event);
    }
    
    // ========== Helper Methods ==========
    
    private void updateSegmentFromData(LayoutSegment segment, NoteBytesObject data) {
        // Update segment properties from data without replacing the entire segment
        // This preserves object identity in the ID map
        
        // Parse updated properties
        NoteBytes layoutNb = data.get("layout") != null ? 
            data.get("layout").getValue() : null;
        if (layoutNb instanceof NoteBytesObject) {
            LayoutSegment.LayoutProperties newLayout = 
                LayoutSegment.LayoutProperties.fromNoteBytesObject((NoteBytesObject) layoutNb);
            
            // Copy properties
            segment.getLayout().display = newLayout.display;
            segment.getLayout().position = newLayout.position;
            segment.getLayout().width = newLayout.width;
            segment.getLayout().height = newLayout.height;
            segment.getLayout().x = newLayout.x;
            segment.getLayout().y = newLayout.y;
            segment.getLayout().margin = newLayout.margin;
            segment.getLayout().padding = newLayout.padding;
            segment.getLayout().backgroundColor = newLayout.backgroundColor;
            segment.getLayout().borderColor = newLayout.borderColor;
            segment.getLayout().borderWidth = newLayout.borderWidth;
        }
        
        NoteBytes styleNb = data.get("style") != null ? 
            data.get("style").getValue() : null;
        if (styleNb instanceof NoteBytesObject) {
            LayoutSegment.StyleProperties newStyle = 
                LayoutSegment.StyleProperties.fromNoteBytesObject((NoteBytesObject) styleNb);
            
            segment.getStyle().fontName = newStyle.fontName;
            segment.getStyle().fontSize = newStyle.fontSize;
            segment.getStyle().bold = newStyle.bold;
            segment.getStyle().italic = newStyle.italic;
            segment.getStyle().textColor = newStyle.textColor;
        }
        
        NoteBytes contentNb = data.get("content") != null ? 
            data.get("content").getValue() : null;
        if (contentNb != null && segment.getType() == LayoutSegment.SegmentType.TEXT) {
            if (contentNb instanceof NoteIntegerArray) {
                segment.setTextContent((NoteIntegerArray) contentNb);
            }
        }
        
        segment.markDirty();
    }
}

/**
 * Helper class to set up bidirectional streaming between VirtualLayoutArea and StreamingLayoutArea
 */
class StreamingLayoutBridge {
    
    private final VirtualLayoutArea m_virtualLayout;
    private final StreamingLayoutArea m_layoutArea;
    
    public StreamingLayoutBridge(VirtualLayoutArea virtualLayout, StreamingLayoutArea layoutArea) {
        m_virtualLayout = virtualLayout;
        m_layoutArea = layoutArea;
        
        // Connect streams
        m_virtualLayout.connectOutboundStream(m_layoutArea::processInboundCommand);
        m_layoutArea.connectOutboundStream(m_virtualLayout::processInboundEvent);
        
        // Enable events
        m_layoutArea.setEventsEnabled(true);
    }
    
    public VirtualLayoutArea getVirtualLayout() {
        return m_virtualLayout;
    }
    
    public StreamingLayoutArea getLayoutArea() {
        return m_layoutArea;
    }
}