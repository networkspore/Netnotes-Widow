package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-side virtual layout that communicates with LayoutCanvas via streams.
 * 
 * This class has NO direct UI access. It builds layouts and sends commands
 * to a remote LayoutCanvas, receiving events back through a feedback stream.
 * 
 * Key Concepts:
 * - Each segment has a unique ID for addressing
 * - Outbound stream: Commands to update UI (add/remove/modify segments)
 * - Inbound stream: Events from UI (cursor moves, text changes, clicks)
 * - Delta updates: Only changed segments are transmitted
 */
public class VirtualLayoutArea {
    
    // ========== ID Management ==========
    
    private final Map<String, LayoutSegment> m_segmentsById;
    private final Map<String, String> m_segmentParents; // segmentId -> parentId
    private int m_nextAutoId = 0;
    
    // ========== Streaming ==========
    
    private Consumer<NoteBytesObject> m_outboundStream;
    private final List<Consumer<LayoutEvent>> m_eventHandlers;
    
    // ========== Root Layout ==========
    
    private LayoutSegment m_root;
    private String m_rootId;
    
    // ========== State Tracking ==========
    
    private final Set<String> m_dirtySegments;
    private boolean m_needsFullSync;
    
    // ========== Constructors ==========
    
    public VirtualLayoutArea() {
        m_segmentsById = new ConcurrentHashMap<>();
        m_segmentParents = new ConcurrentHashMap<>();
        m_eventHandlers = new ArrayList<>();
        m_dirtySegments = ConcurrentHashMap.newKeySet();
        m_needsFullSync = true;
        
        // Create root container
        m_root = new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
        m_rootId = generateId("root");
        m_segmentsById.put(m_rootId, m_root);
    }
    
    // ========== ID Generation ==========
    
    /**
     * Generate unique ID for a segment
     */
    private String generateId(String prefix) {
        return prefix + "_" + (m_nextAutoId++);
    }
    
    public String generateId() {
        return generateId("seg");
    }
    
    // ========== Stream Connection ==========
    
    /**
     * Connect the outbound stream (sends commands to LayoutCanvas)
     */
    public void connectOutboundStream(Consumer<NoteBytesObject> stream) {
        m_outboundStream = stream;
        
        // If we have existing layout, trigger full sync
        if (!m_segmentsById.isEmpty()) {
            m_needsFullSync = true;
            flush();
        }
    }
    
    /**
     * Process inbound event from LayoutCanvas
     */
    public void processInboundEvent(NoteBytesObject eventData) {
        LayoutEvent event = LayoutEvent.fromNoteBytes(eventData);
        
        // Dispatch to registered handlers
        for (Consumer<LayoutEvent> handler : m_eventHandlers) {
            handler.accept(event);
        }
    }
    
    /**
     * Register event handler for specific event types
     */
    public void onEvent(LayoutEvent.EventType type, Consumer<LayoutEvent> handler) {
        m_eventHandlers.add(event -> {
            if (event.type == type) {
                handler.accept(event);
            }
        });
    }
    
    /**
     * Register event handler for specific segment
     */
    public void onSegmentEvent(String segmentId, Consumer<LayoutEvent> handler) {
        m_eventHandlers.add(event -> {
            if (segmentId.equals(event.segmentId)) {
                handler.accept(event);
            }
        });
    }
    
    // ========== Layout Building API ==========
    
    /**
     * Add segment to root with auto-generated ID
     */
    public String addSegment(LayoutSegment segment) {
        return addSegment(generateId(), segment);
    }
    
    /**
     * Add segment to root with specific ID
     */
    public String addSegment(String id, LayoutSegment segment) {
        return addSegmentToParent(m_rootId, id, segment);
    }
    
    /**
     * Add segment as child of another segment
     */
    public String addSegmentToParent(String parentId, String segmentId, LayoutSegment segment) {
        LayoutSegment parent = m_segmentsById.get(parentId);
        if (parent == null || !parent.isContainer()) {
            throw new IllegalArgumentException("Parent must be a container: " + parentId);
        }
        
        // Register segment
        m_segmentsById.put(segmentId, segment);
        m_segmentParents.put(segmentId, parentId);
        
        // Add to parent
        parent.addChild(segment);
        
        // Mark for transmission
        m_dirtySegments.add(segmentId);
        m_dirtySegments.add(parentId); // Parent changed too
        
        return segmentId;
    }
    
    /**
     * Remove segment by ID
     */
    public void removeSegment(String segmentId) {
        String parentId = m_segmentParents.get(segmentId);
        if (parentId == null) {
            return; // Already removed or root
        }
        
        LayoutSegment parent = m_segmentsById.get(parentId);
        if (parent != null && parent.isContainer()) {
            // Find and remove from parent
            NoteBytesArray children = parent.getChildren();
            for (int i = 0; i < children.size(); i++) {
                // Compare by segment identity (we'd need to track this better in real impl)
                LayoutSegment child = parent.getChild(i);
                if (m_segmentsById.get(segmentId) == child) {
                    parent.removeChild(i);
                    break;
                }
            }
            
            m_dirtySegments.add(parentId);
        }
        
        // Unregister
        m_segmentsById.remove(segmentId);
        m_segmentParents.remove(segmentId);
        
        // Send remove command
        sendCommand(LayoutCommand.remove(segmentId));
    }
    
    /**
     * Update segment properties
     */
    public void updateSegment(String segmentId, Consumer<LayoutSegment> updater) {
        LayoutSegment segment = m_segmentsById.get(segmentId);
        if (segment == null) {
            throw new IllegalArgumentException("Segment not found: " + segmentId);
        }
        
        updater.accept(segment);
        segment.markDirty();
        m_dirtySegments.add(segmentId);
    }
    
    /**
     * Get segment by ID
     */
    public LayoutSegment getSegment(String segmentId) {
        return m_segmentsById.get(segmentId);
    }
    
    // ========== Flushing Changes ==========
    
    /**
     * Flush all pending changes to the outbound stream
     */
    public void flush() {
        if (m_outboundStream == null) {
            return; // Not connected
        }
        
        if (m_needsFullSync) {
            sendFullSync();
            m_needsFullSync = false;
            m_dirtySegments.clear();
        } else if (!m_dirtySegments.isEmpty()) {
            sendDeltaUpdates();
            m_dirtySegments.clear();
        }
    }
    
    /**
     * Send full layout tree (initial sync or after reconnect)
     */
    private void sendFullSync() {
        // Build full segment tree
        NoteBytesArray segments = m_root.getChildren();
        
        LayoutCommand cmd = LayoutCommand.fullSync(segments);
        sendCommand(cmd);
    }
    
    /**
     * Send only changed segments
     */
    private void sendDeltaUpdates() {
        for (String segmentId : m_dirtySegments) {
            LayoutSegment segment = m_segmentsById.get(segmentId);
            if (segment != null) {
                LayoutCommand cmd = LayoutCommand.update(segmentId, segment);
                sendCommand(cmd);
            }
        }
    }
    
    /**
     * Send a command to the LayoutCanvas
     */
    private void sendCommand(LayoutCommand command) {
        if (m_outboundStream != null) {
            m_outboundStream.accept(command.toNoteBytes());
        }
    }
    
    // ========== Async Operations ==========
    
    /**
     * Send command and wait for specific event response
     */
    public CompletableFuture<LayoutEvent> sendAndWaitForEvent(
        LayoutCommand command, 
        LayoutEvent.EventType expectedType
    ) {
        CompletableFuture<LayoutEvent> future = new CompletableFuture<>();
        
        // Register one-time handler
        Consumer<LayoutEvent> handler = new Consumer<LayoutEvent>() {
            @Override
            public void accept(LayoutEvent event) {
                if (event.type == expectedType) {
                    future.complete(event);
                    m_eventHandlers.remove(this);
                }
            }
        };
        
        m_eventHandlers.add(handler);
        sendCommand(command);
        
        return future;
    }
    
    // ========== Convenience Methods ==========
    
    public String addText(String text) {
        LayoutSegment segment = LayoutCanvas.createTextSegment(text);
        return addSegment(segment);
    }
    
    public String addText(String id, String text) {
        LayoutSegment segment = LayoutCanvas.createTextSegment(text);
        return addSegment(id, segment);
    }
    
    public String addContainer() {
        LayoutSegment segment = LayoutCanvas.createContainer();
        return addSegment(segment);
    }
    
    public String addContainer(String id) {
        LayoutSegment segment = LayoutCanvas.createContainer();
        return addSegment(id, segment);
    }
    
    public void setText(String segmentId, String text) {
        updateSegment(segmentId, segment -> {
            if (segment.getType() == LayoutSegment.SegmentType.TEXT) {
                segment.setTextContent(text);
            }
        });
    }
    
    public void setStyle(String segmentId, Consumer<LayoutSegment.StyleProperties> styler) {
        updateSegment(segmentId, segment -> styler.accept(segment.getStyle()));
    }
    
    public void setLayout(String segmentId, Consumer<LayoutSegment.LayoutProperties> layouter) {
        updateSegment(segmentId, segment -> layouter.accept(segment.getLayout()));
    }
    
    // ========== Root Access ==========
    
    public LayoutSegment getRoot() {
        return m_root;
    }
    
    public String getRootId() {
        return m_rootId;
    }
}

/**
 * Command sent from VirtualLayoutArea to LayoutCanvas
 */
class LayoutCommand {
    enum CommandType {
        FULL_SYNC(0),
        UPDATE(1),
        REMOVE(2),
        INSERT(3),
        MOVE(4);
        
        final int value;
        CommandType(int value) { this.value = value; }
    }
    
    CommandType type;
    String segmentId;
    NoteBytesArray segments; // For FULL_SYNC
    NoteBytesObject segmentData; // For UPDATE/INSERT
    int index; // For INSERT
    
    static LayoutCommand fullSync(NoteBytesArray segments) {
        LayoutCommand cmd = new LayoutCommand();
        cmd.type = CommandType.FULL_SYNC;
        cmd.segments = segments;
        return cmd;
    }
    
    static LayoutCommand update(String segmentId, LayoutSegment segment) {
        LayoutCommand cmd = new LayoutCommand();
        cmd.type = CommandType.UPDATE;
        cmd.segmentId = segmentId;
        cmd.segmentData = segment.getData();
        return cmd;
    }
    
    static LayoutCommand remove(String segmentId) {
        LayoutCommand cmd = new LayoutCommand();
        cmd.type = CommandType.REMOVE;
        cmd.segmentId = segmentId;
        return cmd;
    }
    
    static LayoutCommand insert(String segmentId, LayoutSegment segment, int index) {
        LayoutCommand cmd = new LayoutCommand();
        cmd.type = CommandType.INSERT;
        cmd.segmentId = segmentId;
        cmd.segmentData = segment.getData();
        cmd.index = index;
        return cmd;
    }
    
    NoteBytesObject toNoteBytes() {
        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("type", new NoteInteger(type.value))
        });
        
        if (segmentId != null) {
            obj.add("segmentId", new NoteString(segmentId));
        }
        if (segments != null) {
            obj.add("segments", segments);
        }
        if (segmentData != null) {
            obj.add("segmentData", segmentData);
        }
        if (index >= 0) {
            obj.add("index", new NoteInteger(index));
        }
        
        return obj;
    }
}

/**
 * Event received from LayoutCanvas back to VirtualLayoutArea
 */
class LayoutEvent {
    enum EventType {
        CURSOR_MOVED(0),
        TEXT_CHANGED(1),
        SELECTION_CHANGED(2),
        SEGMENT_CLICKED(3),
        SEGMENT_FOCUSED(4),
        LAYOUT_COMPLETE(5);
        
        final int value;
        EventType(int value) { this.value = value; }
        
        static EventType fromValue(int value) {
            for (EventType t : values()) {
                if (t.value == value) return t;
            }
            return CURSOR_MOVED;
        }
    }
    
    EventType type;
    String segmentId;
    Map<String, NoteBytes> data; // Flexible data payload
    
    static LayoutEvent cursorMoved(String segmentId, int offset) {
        LayoutEvent event = new LayoutEvent();
        event.type = EventType.CURSOR_MOVED;
        event.segmentId = segmentId;
        event.data = new HashMap<>();
        event.data.put("offset", new NoteInteger(offset));
        return event;
    }
    
    static LayoutEvent textChanged(String segmentId, String newText) {
        LayoutEvent event = new LayoutEvent();
        event.type = EventType.TEXT_CHANGED;
        event.segmentId = segmentId;
        event.data = new HashMap<>();
        event.data.put("text", new NoteString(newText));
        return event;
    }
    
    static LayoutEvent segmentClicked(String segmentId) {
        LayoutEvent event = new LayoutEvent();
        event.type = EventType.SEGMENT_CLICKED;
        event.segmentId = segmentId;
        event.data = new HashMap<>();
        return event;
    }

    static LayoutEvent layoutComplete() {
        LayoutEvent event = new LayoutEvent();
        event.type = EventType.LAYOUT_COMPLETE;
        event.data = new HashMap<>();
        return event;
    }
    
    static LayoutEvent fromNoteBytes(NoteBytesObject obj) {
        LayoutEvent event = new LayoutEvent();
        
        NoteBytes typeNb = obj.get("type") != null ? obj.get("type").getValue() : null;
        event.type = typeNb != null ? 
            EventType.fromValue(typeNb.getAsInt()) : EventType.CURSOR_MOVED;
        
        NoteBytes idNb = obj.get("segmentId") != null ? obj.get("segmentId").getValue() : null;
        event.segmentId = idNb != null ? idNb.getAsString() : null;
        
        // Parse data map
        event.data = new HashMap<>();
        NoteBytes dataNb = obj.get("data") != null ? obj.get("data").getValue() : null;
        if (dataNb instanceof NoteBytesObject) {
            NoteBytesObject dataObj = (NoteBytesObject) dataNb;
            for (NoteBytesPair pair : dataObj.getAsList()) {
                event.data.put(pair.getKey().getAsString(), pair.getValue());
            }
        }
        
        return event;
    }
    
    NoteBytesObject toNoteBytes() {
        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("type", new NoteInteger(type.value))
        });
        
        if (segmentId != null) {
            obj.add("segmentId", new NoteString(segmentId));
        }
        
        if (data != null && !data.isEmpty()) {
            NoteBytesPair[] pairs = new NoteBytesPair[data.size()];
            int i = 0;
            for (Map.Entry<String, NoteBytes> entry : data.entrySet()) {
                pairs[i++] = new NoteBytesPair(entry.getKey(), entry.getValue());
            }
            obj.add("data", new NoteBytesObject(pairs));
        }
        
        return obj;
    }
}