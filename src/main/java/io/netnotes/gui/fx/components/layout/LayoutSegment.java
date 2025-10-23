package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBigDecimal;
import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteDouble;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.gui.fx.components.images.scaling.ScalingUtils.ScalingAlgorithm;
import io.netnotes.gui.fx.noteBytes.NoteBytesImage;

import java.awt.*;
import java.math.BigDecimal;

/**
 * Foundation for a unified layout/editing system.
 * Uses binary data types directly - NO STRING PARSING!
 * 
 * Key Design Principles:
 * 1. Cursor traversal - All segments are part of the cursor navigation tree
 * 2. Selective editability - Per-segment editable flag
 * 3. Event transparency - Segments can pass through mouse/keyboard events
 * 4. Container hierarchy - Segments can contain other segments
 * 5. Layout properties - Position, size, display mode
 * 6. Binary-first - Leverage NoteBytes type system, no text parsing
 */
public class LayoutSegment {
    
    // ========== Enums ==========
    
    /**
     * Segment type determines content interpretation and rendering
     */
    public enum SegmentType {
        TEXT(0),
        CONTAINER(1),
        IMAGE(2),
        SPACER(3),
        COMPONENT(4);
        
        private final int value;
        
        SegmentType(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static SegmentType fromValue(int value) {
            for (SegmentType type : values()) {
                if (type.value == value) return type;
            }
            return TEXT;
        }
    }
    
    /**
     * Display mode determines layout behavior
     */
    public enum Display {
        BLOCK(0),
        INLINE(1),
        INLINE_BLOCK(2),
        NONE(3),
        HIDDEN(4);
        
        private final int value;
        
        Display(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static Display fromValue(int value) {
            for (Display d : values()) {
                if (d.value == value) return d;
            }
            return BLOCK;
        }
    }
    
    /**
     * Position type determines coordinate system
     */
    public enum Position {
        STATIC(0),
        RELATIVE(1),
        ABSOLUTE(2),
        FIXED(3);
        
        private final int value;
        
        Position(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static Position fromValue(int value) {
            for (Position p : values()) {
                if (p.value == value) return p;
            }
            return STATIC;
        }
    }
    
    /**
     * Float type for wrapping behavior
     */
    public enum FloatType {
        NONE(0),
        LEFT(1),
        RIGHT(2);
        
        private final int value;
        
        FloatType(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static FloatType fromValue(int value) {
            for (FloatType f : values()) {
                if (f.value == value) return f;
            }
            return NONE;
        }
    }
    
    /**
     * Dimension value - stored as binary, not parsed text!
     * Can be pixels, percentage, or auto
     */
    public static class Dimension {
        public enum Unit { 
            PX(0),
            PERCENT(1),
            AUTO(2);
            
            private final int value;
            
            Unit(int value) { this.value = value; }
            public int getValue() { return value; }
            
            public static Unit fromValue(int value) {
                for (Unit u : values()) {
                    if (u.value == value) return u;
                }
                return AUTO;
            }
        }
        
        private final double value;
        private final Unit unit;
        
        private Dimension(double value, Unit unit) {
            this.value = value;
            this.unit = unit;
        }
        
        public static Dimension px(double value) {
            return new Dimension(value, Unit.PX);
        }
        
        public static Dimension percent(double value) {
            return new Dimension(value, Unit.PERCENT);
        }
        
        public static Dimension auto() {
            return new Dimension(0, Unit.AUTO);
        }
        
        /**
         * Load from NoteBytes - NO PARSING!
         * Reads binary data directly based on type
         */
        public static Dimension fromNoteBytes(NoteBytes nb) {
            if (nb == null) return auto();
            
            // Check if it's an object with unit + value
            if (nb.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesObject nbo = nb.getAsNoteBytesObject();
                
                NoteBytes unitNb = nbo.get("unit") != null ? nbo.get("unit").getValue() : null;
                NoteBytes valueNb = nbo.get("value") != null ? nbo.get("value").getValue() : null;
                
                if (unitNb != null) {
                    int unitValue = unitNb.getAsInt();
                    Unit unit = Unit.fromValue(unitValue);
                    
                    double value = 0;
                    if (valueNb != null && unit != Unit.AUTO) {
                        // Direct binary read - no parsing!
                        if (valueNb.getType() == NoteBytesMetaData.DOUBLE_TYPE || 
                            valueNb.getType() == NoteBytesMetaData.DOUBLE_LE_TYPE) {
                            value = valueNb.getAsDouble();
                        } else if (valueNb.getType() == NoteBytesMetaData.INTEGER_TYPE ||
                                   valueNb.getType() == NoteBytesMetaData.INTEGER_LE_TYPE) {
                            value = valueNb.getAsInt();
                        } else if (valueNb.getType() == NoteBytesMetaData.FLOAT_TYPE ||
                                   valueNb.getType() == NoteBytesMetaData.FLOAT_LE_TYPE) {
                            value = valueNb.getAsFloat();
                        }
                    }
                    
                    return new Dimension(value, unit);
                }
            }
            
            // Fallback: treat as direct pixel value
            if (nb.getType() == NoteBytesMetaData.DOUBLE_TYPE) {
                return px(nb.getAsDouble());
            } else if (nb.getType() == NoteBytesMetaData.INTEGER_TYPE) {
                return px(nb.getAsInt());
            } else if (nb.getType() == NoteBytesMetaData.FLOAT_TYPE) {
                return px(nb.getAsFloat());
            }
            
            return auto();
        }
        
   
        public NoteBytes toNoteBytes() {
            return new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair("unit", new NoteInteger(unit.getValue())),
                new NoteBytesPair("value", new NoteDouble(value))
            });
        }
        
        public int resolve(int containerSize) {
            switch (unit) {
                case PX: return (int) value;
                case PERCENT: return (int) (containerSize * value / 100.0);
                case AUTO: return 0; // Caller should handle
                default: return 0;
            }
        }
        
        public boolean isAuto() {
            return unit == Unit.AUTO;
        }
        
        public double getValue() { return value; }
        public Unit getUnit() { return unit; }
        
        @Override
        public String toString() {
            // Only for debugging/display
            switch (unit) {
                case PX: return value + "px";
                case PERCENT: return value + "%";
                case AUTO: return "auto";
                default: return "0px";
            }
        }
    }
    
    /**
     * Layout properties for a segment
     * All stored as binary types
     */
    public static class LayoutProperties {
        public Display display = Display.BLOCK;
        public Position position = Position.STATIC;
        public FloatType floatType = FloatType.NONE;
        
        public Dimension width = Dimension.auto();
        public Dimension height = Dimension.auto();
        public Dimension x = Dimension.auto();
        public Dimension y = Dimension.auto();
        
        public Insets margin = new Insets(0, 0, 0, 0);
        public Insets padding = new Insets(0, 0, 0, 0);
        
        public int zIndex = 0;
        public BigDecimal aspectRatio = null;
        public ScalingAlgorithm scalingAlgorithm = ScalingAlgorithm.BILINEAR;
    
        public Color backgroundColor = null;
        public Color borderColor = null;
        public int borderWidth = 0;
        
        /**
         * Load from binary object - NO STRING PARSING!
         */
        public static LayoutProperties fromNoteBytesObject(NoteBytesObject nbo) {
            LayoutProperties props = new LayoutProperties();
            
            if (nbo == null) return props;
            
            // Display - as integer enum
            NoteBytes displayNb = nbo.get("display") != null ? nbo.get("display").getValue() : null;
            if (displayNb != null) {
                props.display = Display.fromValue(displayNb.getAsInt());
            }
            
            // Position - as integer enum
            NoteBytes positionNb = nbo.get("position") != null ? nbo.get("position").getValue() : null;
            if (positionNb != null) {
                props.position = Position.fromValue(positionNb.getAsInt());
            }
            
            // Float - as integer enum
            NoteBytes floatNb = nbo.get("float") != null ? nbo.get("float").getValue() : null;
            if (floatNb != null) {
                props.floatType = FloatType.fromValue(floatNb.getAsInt());
            }
            
            // Dimensions - NO PARSING!
            NoteBytes widthNb = nbo.get("width") != null ? nbo.get("width").getValue() : null;
            if (widthNb != null) {
                props.width = Dimension.fromNoteBytes(widthNb);
            }
            
            NoteBytes heightNb = nbo.get("height") != null ? nbo.get("height").getValue() : null;
            if (heightNb != null) {
                props.height = Dimension.fromNoteBytes(heightNb);
            }
            
            NoteBytes xNb = nbo.get("x") != null ? nbo.get("x").getValue() : null;
            if (xNb != null) {
                props.x = Dimension.fromNoteBytes(xNb);
            }
            
            NoteBytes yNb = nbo.get("y") != null ? nbo.get("y").getValue() : null;
            if (yNb != null) {
                props.y = Dimension.fromNoteBytes(yNb);
            }
            
            // Z-index - direct int
            NoteBytes zIndexNb = nbo.get("zIndex") != null ? nbo.get("zIndex").getValue() : null;
            if (zIndexNb != null) {
                props.zIndex = zIndexNb.getAsInt();
            }
            
            // Aspect ratio - direct double
            NoteBytes aspectRatioNb = nbo.get("aspectRatio") != null ? nbo.get("aspectRatio").getValue() : null;
            if (aspectRatioNb != null) {
                props.aspectRatio = ByteDecoding.readAsBigDecimal(aspectRatioNb);
            }

            NoteBytes scalingAlgNb = nbo.get("scalingAlgorithm") != null ? 
                nbo.get("scalingAlgorithm").getValue() : null;
            if (scalingAlgNb != null) {
                props.scalingAlgorithm = ScalingAlgorithm.fromValue(scalingAlgNb.getAsInt());
            }
            
            // Colors - as integers with alpha
            NoteBytes bgColorNb = nbo.get("backgroundColor") != null ? nbo.get("backgroundColor").getValue() : null;
            if (bgColorNb != null) {
                props.backgroundColor = new Color(bgColorNb.getAsInt(), true);
            }
            
            NoteBytes borderColorNb = nbo.get("borderColor") != null ? nbo.get("borderColor").getValue() : null;
            if (borderColorNb != null) {
                props.borderColor = new Color(borderColorNb.getAsInt(), true);
            }
            
            NoteBytes borderWidthNb = nbo.get("borderWidth") != null ? nbo.get("borderWidth").getValue() : null;
            if (borderWidthNb != null) {
                props.borderWidth = borderWidthNb.getAsInt();
            }
            
            // Margins and padding - single int (could be expanded to per-side)
            NoteBytes marginNb = nbo.get("margin") != null ? nbo.get("margin").getValue() : null;
            if (marginNb != null) {
                int m = marginNb.getAsInt();
                props.margin = new Insets(m, m, m, m);
            }
            
            NoteBytes paddingNb = nbo.get("padding") != null ? nbo.get("padding").getValue() : null;
            if (paddingNb != null) {
                int p = paddingNb.getAsInt();
                props.padding = new Insets(p, p, p, p);
            }
            
            return props;
        }

    
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesPair[] pairs = {
                new NoteBytesPair("display", new NoteInteger(display.getValue())),
                new NoteBytesPair("position", new NoteInteger(position.getValue())),
                new NoteBytesPair("float", new NoteInteger(floatType.getValue())),
                
                new NoteBytesPair("width", width.toNoteBytes()),
                new NoteBytesPair("height", height.toNoteBytes()),
                new NoteBytesPair("x", x.toNoteBytes()),
                new NoteBytesPair("y", y.toNoteBytes()),
                
                new NoteBytesPair("zIndex", new NoteInteger(zIndex)),
                new NoteBytesPair("aspectRatio", new NoteBigDecimal(aspectRatio)),
                new NoteBytesPair("scalingAlgorithm", new NoteInteger(scalingAlgorithm.getValue())),
                new NoteBytesPair("borderWidth", new NoteInteger(borderWidth)),
                
                new NoteBytesPair("margin", new NoteInteger(margin.top)),
                new NoteBytesPair("padding", new NoteInteger(padding.top))
            };

            NoteBytesObject nbo = new NoteBytesObject(pairs);
            
            if (backgroundColor != null) {
                nbo.add("backgroundColor", new NoteInteger(backgroundColor.getRGB()));
            }
            
            if (borderColor != null) {
                nbo.add("borderColor", new NoteInteger(borderColor.getRGB()));
            }
            
            return nbo;
        }
    }
    
    /**
     * Interaction properties for a segment
     */
    public static class InteractionProperties {
        public boolean editable = true;
        public boolean selectable = true;
        public boolean focusable = true;
        public boolean mouseTransparent = false;
        public boolean keyboardTransparent = false;
        
        public static InteractionProperties fromNoteBytesObject(NoteBytesObject nbo) {
            InteractionProperties props = new InteractionProperties();
            
            if (nbo == null) return props;
            
            NoteBytes editableNb = nbo.get("editable") != null ? nbo.get("editable").getValue() : null;
            if (editableNb != null) {
                props.editable = editableNb.getAsBoolean();
            }
            
            NoteBytes selectableNb = nbo.get("selectable") != null ? nbo.get("selectable").getValue() : null;
            if (selectableNb != null) {
                props.selectable = selectableNb.getAsBoolean();
            }
            
            NoteBytes focusableNb = nbo.get("focusable") != null ? nbo.get("focusable").getValue() : null;
            if (focusableNb != null) {
                props.focusable = focusableNb.getAsBoolean();
            }
            
            NoteBytes mouseTransparentNb = nbo.get("mouseTransparent") != null ? nbo.get("mouseTransparent").getValue() : null;
            if (mouseTransparentNb != null) {
                props.mouseTransparent = mouseTransparentNb.getAsBoolean();
            }
            
            NoteBytes keyboardTransparentNb = nbo.get("keyboardTransparent") != null ? nbo.get("keyboardTransparent").getValue() : null;
            if (keyboardTransparentNb != null) {
                props.keyboardTransparent = keyboardTransparentNb.getAsBoolean();
            }
            
            return props;
        }
        
        public NoteBytesObject toNoteBytesObject() {
            return new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair("editable", new NoteBoolean(editable)),
                new NoteBytesPair("selectable", new NoteBoolean(selectable)),
                new NoteBytesPair("focusable", new NoteBoolean(focusable)),
                new NoteBytesPair("mouseTransparent", new NoteBoolean(mouseTransparent)),
                new NoteBytesPair("keyboardTransparent", new NoteBoolean(keyboardTransparent))
            });
        }
    }
    
    /**
     * Style properties for text/appearance
     */
    public static class StyleProperties {
        public String fontName = "Monospaced";
        public int fontSize = 14;
        public boolean bold = false;
        public boolean italic = false;
        public Color textColor = Color.BLACK;
        
        public Font getFont() {
            int style = Font.PLAIN;
            if (bold && italic) style = Font.BOLD | Font.ITALIC;
            else if (bold) style = Font.BOLD;
            else if (italic) style = Font.ITALIC;
            return new Font(fontName, style, fontSize);
        }
        
        public static StyleProperties fromNoteBytesObject(NoteBytesObject nbo) {
            StyleProperties props = new StyleProperties();
            
            if (nbo == null) return props;
            
            NoteBytes fontNameNb = nbo.get("fontName") != null ? nbo.get("fontName").getValue() : null;
            if (fontNameNb != null) {
                props.fontName = fontNameNb.getAsString();
            }
            
            NoteBytes fontSizeNb = nbo.get("fontSize") != null ? nbo.get("fontSize").getValue() : null;
            if (fontSizeNb != null) {
                props.fontSize = fontSizeNb.getAsInt();
            }
            
            NoteBytes boldNb = nbo.get("bold") != null ? nbo.get("bold").getValue() : null;
            if (boldNb != null) {
                props.bold = boldNb.getAsBoolean();
            }
            
            NoteBytes italicNb = nbo.get("italic") != null ? nbo.get("italic").getValue() : null;
            if (italicNb != null) {
                props.italic = italicNb.getAsBoolean();
            }
            
            NoteBytes textColorNb = nbo.get("textColor") != null ? nbo.get("textColor").getValue() : null;
            if (textColorNb != null) {
                props.textColor = new Color(textColorNb.getAsInt(), true);
            }
            
            return props;
        }
        
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesPair[] pairs = {
                new NoteBytesPair("fontName", new NoteString(fontName)),
                new NoteBytesPair("fontSize", new NoteInteger(fontSize)),
                new NoteBytesPair("bold", new NoteBoolean(bold)),
                new NoteBytesPair("italic", new NoteBoolean(italic)),
                new NoteBytesPair("textColor", new NoteInteger(textColor.getRGB()))
            };
            return new NoteBytesObject(pairs);
        }
    }
    
    // ========== Segment Data ==========
    
    private NoteBytesObject m_data;

    private boolean m_dataDirty = false;
    private SegmentType m_type;
    private LayoutProperties m_layout;
    private InteractionProperties m_interaction;
    private StyleProperties m_style;
    
    // Content (depends on type)
    private NoteIntegerArray m_textContent;
    private NoteBytesArray m_children;
    private NoteBytes m_binaryContent;
    
    // Computed layout (runtime only, not stored)
    private Rectangle m_bounds;
    private int m_cursorStartOffset;
    private int m_cursorEndOffset;
    
    // ========== Constructors ==========
    
    public LayoutSegment(NoteBytesObject data) {
        m_data = data;
        parseData();
    }
    
    public LayoutSegment(SegmentType type) {
        m_type = type;
        m_layout = new LayoutProperties();
        m_interaction = new InteractionProperties();
        m_style = new StyleProperties();
        
        switch (type) {
            case TEXT:
                m_textContent = new NoteIntegerArray();
                break;
            case CONTAINER:
                m_children = new NoteBytesArray();
                break;
            case IMAGE:
                m_binaryContent = new NoteBytes(new byte[0]);
                break;
            default:
                break;
        }
        
        rebuildData();
    }
    
    // ========== Data Parsing ==========
    
    private void parseData() {
        // Type
        NoteBytes typeNb = m_data.get("type") != null ? m_data.get("type").getValue() : null;
        m_type = typeNb != null ? SegmentType.fromValue(typeNb.getAsInt()) : SegmentType.TEXT;
        
        // Layout
        NoteBytes layoutNb = m_data.get("layout") != null ? m_data.get("layout").getValue() : null;
        m_layout = layoutNb instanceof NoteBytesObject ? 
            LayoutProperties.fromNoteBytesObject((NoteBytesObject) layoutNb) : 
            new LayoutProperties();
        
        // Interaction
        NoteBytes interactionNb = m_data.get("interaction") != null ? m_data.get("interaction").getValue() : null;
        m_interaction = interactionNb instanceof NoteBytesObject ? 
            InteractionProperties.fromNoteBytesObject((NoteBytesObject) interactionNb) : 
            new InteractionProperties();
        
        // Style
        NoteBytes styleNb = m_data.get("style") != null ? m_data.get("style").getValue() : null;
        m_style = styleNb instanceof NoteBytesObject ? 
            StyleProperties.fromNoteBytesObject((NoteBytesObject) styleNb) : 
            new StyleProperties();
        
        // Content (type-specific)
        NoteBytes contentNb = m_data.get("content") != null ? m_data.get("content").getValue() : null;
        
        switch (m_type) {
            case TEXT:
                m_textContent = contentNb instanceof NoteIntegerArray ? 
                    (NoteIntegerArray) contentNb : new NoteIntegerArray();
                break;
            case CONTAINER:
                m_children = contentNb instanceof NoteBytesArray ? 
                    (NoteBytesArray) contentNb : new NoteBytesArray();
                break;
            case IMAGE:
                m_binaryContent = contentNb != null ? contentNb : new NoteBytes(new byte[0]);
                break;
            default:
                break;
        }
    }
    
    private void rebuildData() {
        m_data = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("type", new NoteInteger(m_type.getValue())),
            new NoteBytesPair("layout", m_layout.toNoteBytesObject()),
            new NoteBytesPair("interaction", m_interaction.toNoteBytesObject()),
            new NoteBytesPair("style", m_style.toNoteBytesObject())
        });
        
        // Content
        switch (m_type) {
            case TEXT:
                if (m_textContent != null) {
                    m_data.add("content", m_textContent);
                }
                break;
            case CONTAINER:
                if (m_children != null) {
                    m_data.add("content", m_children);
                }
                break;
            case IMAGE:
                if (m_binaryContent != null) {
                    m_data.add("content", m_binaryContent);
                }
                break;
            default:
                break;
        }
    }
    
    // ========== Getters/Setters ==========
    
    public SegmentType getType() { return m_type; }
    public LayoutProperties getLayout() { return m_layout; }
    public InteractionProperties getInteraction() { return m_interaction; }
    public StyleProperties getStyle() { return m_style; }
    
    public NoteIntegerArray getTextContent() { return m_textContent; }
    public NoteBytesArray getChildren() { return m_children; }
    public NoteBytes getBinaryContent() { return m_binaryContent; }
    
    public Rectangle getBounds() { return m_bounds; }
    public void setBounds(Rectangle bounds) { m_bounds = bounds; }
    
    public int getCursorStartOffset() { return m_cursorStartOffset; }
    public void setCursorStartOffset(int offset) { m_cursorStartOffset = offset; }
    
    public int getCursorEndOffset() { return m_cursorEndOffset; }
    public void setCursorEndOffset(int offset) { m_cursorEndOffset = offset; }
    
    public NoteBytesObject getData() { 
        if (m_dataDirty) {
            rebuildData();
            m_dataDirty = false;
        }
        return m_data; 
    }
    
    // ========== Binary Content Setters ==========
    
    public void setBinaryContent(NoteBytes content) {
        if (m_type != SegmentType.IMAGE) {
            throw new IllegalStateException("Binary content can only be set on IMAGE segments");
        }
        m_binaryContent = content;
        m_dataDirty = true; 
    }
    
    public void setBinaryContent(byte[] data) {
        setBinaryContent(new NoteBytes(data));
    }
    
    public void setImageContent(NoteBytesImage image) {
        setBinaryContent(image);
    }
    
    // ========== Text Content Setters ==========
    
    public void setTextContent(String text) {
        if (m_type != SegmentType.TEXT) {
            throw new IllegalStateException("Text content can only be set on TEXT segments");
        }
        if (m_textContent == null) {
            m_textContent = new NoteIntegerArray();
        }
        m_textContent.set(text);
        m_dataDirty = true;
    }
    
    public void setTextContent(NoteIntegerArray content) {
        if (m_type != SegmentType.TEXT) {
            throw new IllegalStateException("Text content can only be set on TEXT segments");
        }
        m_textContent = content;
        m_dataDirty = true;
    }
    
    // ========== Utility Methods ==========
    
    public boolean isContainer() {
        return m_type == SegmentType.CONTAINER;
    }
    
    public boolean hasChildren() {
        return isContainer() && m_children != null && m_children.size() > 0;
    }
    
    // ========== Children Management ==========
    
    public void addChild(LayoutSegment child) {
        if (!isContainer()) {
            throw new IllegalStateException("Can only add children to CONTAINER segments");
        }
        if (m_children == null) {
            m_children = new NoteBytesArray();
        }
        m_children.add(child.getData());
        m_dataDirty = true; 
    }
    
    public void addChild(int index, LayoutSegment child) {
        if (!isContainer()) {
            throw new IllegalStateException("Can only add children to CONTAINER segments");
        }
        if (m_children == null) {
            m_children = new NoteBytesArray();
        }
        m_children.add(index, child.getData());
        rebuildData();
    }
    
    public void removeChild(int index) {
        if (!isContainer()) {
            throw new IllegalStateException("Can only remove children from CONTAINER segments");
        }
        if (m_children != null && index >= 0 && index < m_children.size()) {
            m_children.remove(index);
            rebuildData();
        }
    }
    
    public void clearChildren() {
        if (!isContainer()) {
            throw new IllegalStateException("Can only clear children on CONTAINER segments");
        }
        if (m_children != null) {
            m_children.clear();
            rebuildData();
        }
    }
    
    public int getChildCount() {
        return isContainer() && m_children != null ? m_children.size() : 0;
    }
    
    public LayoutSegment getChild(int index) {
        if (!isContainer() || m_children == null) {
            return null;
        }
        
        if (index >= 0 && index < m_children.size()) {
            NoteBytes item = m_children.get(index);
            if (item instanceof NoteBytesObject) {
                return new LayoutSegment((NoteBytesObject) item);
            }
        }
        
        return null;
    }

    public boolean isDirty(){
        return m_dataDirty;
    }

    public void markDirty(){
        m_dataDirty = true;
    }
    
    // ========== Builder-Style Methods ==========
    
    public LayoutSegment withDisplay(Display display) {
        m_layout.display = display;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withWidth(Dimension width) {
        m_layout.width = width;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withHeight(Dimension height) {
        m_layout.height = height;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withPadding(Insets padding) {
        m_layout.padding = padding;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withMargin(Insets margin) {
        m_layout.margin = margin;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withBackgroundColor(Color color) {
        m_layout.backgroundColor = color;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withBorder(Color color, int width) {
        m_layout.borderColor = color;
        m_layout.borderWidth = width;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withEditable(boolean editable) {
        m_interaction.editable = editable;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withFocusable(boolean focusable) {
        m_interaction.focusable = focusable;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withSelectable(boolean selectable) {
        m_interaction.selectable = selectable;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withFontSize(int size) {
        m_style.fontSize = size;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withBold(boolean bold) {
        m_style.bold = bold;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withItalic(boolean italic) {
        m_style.italic = italic;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withTextColor(Color color) {
        m_style.textColor = color;
        m_dataDirty = true;
        return this;
    }
    
    public LayoutSegment withAspectRatio(BigDecimal aspectRatio) {
        m_layout.aspectRatio = aspectRatio;
        m_dataDirty = true;
        return this;
    }

    public LayoutSegment withScalingAlgorithm(ScalingAlgorithm algorithm) {
        m_layout.scalingAlgorithm = algorithm;
        m_dataDirty = true;
        return this;
    }
    // ========== Content Length ==========
    
    public int getContentLength() {
        switch (m_type) {
            case TEXT:
                return m_textContent != null ? m_textContent.length() : 0;
            case CONTAINER:
                // Recursively count children
                int total = 0;
                if (m_children != null) {
                    for (int i = 0; i < m_children.size(); i++) {
                        NoteBytes child = m_children.get(i);
                        if (child instanceof NoteBytesObject) {
                            LayoutSegment childSeg = new LayoutSegment((NoteBytesObject) child);
                            total += childSeg.getContentLength();
                        }
                    }
                }
                return total;
            default:
                return 1; // Non-text content counts as 1 position
        }
    }
    
    @Override
    public String toString() {
        return String.format("LayoutSegment[type=%s, editable=%s, selectable=%s, display=%s]",
            m_type, m_interaction.editable, m_interaction.selectable, m_layout.display);
    }
}