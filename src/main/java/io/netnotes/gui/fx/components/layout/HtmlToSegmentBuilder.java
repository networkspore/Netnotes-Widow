package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.gui.fx.noteBytes.NoteBytesImage;
import io.netnotes.gui.fx.utils.TaskUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts HTML markup to LayoutSegment structures.
 * Supports basic HTML tags: p, h1-h6, strong, b, em, i, a, img, div, span, br, table
 * Supports grid layouts via table elements and custom data attributes
 */
public class HtmlToSegmentBuilder {
    
    // Simple HTML tag regex (doesn't handle all edge cases, but works for basic HTML)
    private static final Pattern TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)(.*?)(/?)>", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)\\s*=\\s*[\"']([^\"']*)[\"']");
    
    /**
     * Parse HTML string into segments
     */
    public static NoteBytesArray buildFromHtml(String html) {
        NoteBytesArray segments = new NoteBytesArray();
        
        if (html == null || html.trim().isEmpty()) {
            return segments;
        }
        
        // Parse HTML into a simple DOM tree
        List<HtmlNode> nodes = parseHtml(html);
        
        // Convert nodes to segments
        for (HtmlNode node : nodes) {
            LayoutSegment segment = convertNode(node);
            if (segment != null) {
                segments.add(segment.getData());
            }
        }
        
        return segments;
    }
    
    /**
     * Simple HTML parser that builds a tree of nodes
     */
    private static List<HtmlNode> parseHtml(String html) {
        List<HtmlNode> roots = new ArrayList<>();
        List<HtmlNode> stack = new ArrayList<>();
        
        Matcher matcher = TAG_PATTERN.matcher(html);
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Add text before tag
            if (matcher.start() > lastEnd) {
                String text = html.substring(lastEnd, matcher.start());
                if (!text.trim().isEmpty()) {
                    HtmlNode textNode = new HtmlNode("text");
                    textNode.text = unescapeHtml(text);
                    addNode(textNode, stack, roots);
                }
            }
            
            String isClosing = matcher.group(1);
            String tagName = matcher.group(2).toLowerCase();
            String attributes = matcher.group(3);
            String isSelfClosing = matcher.group(4);
            
            if (!isClosing.isEmpty()) {
                // Closing tag
                if (!stack.isEmpty() && stack.get(stack.size() - 1).tag.equals(tagName)) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                // Opening tag
                HtmlNode node = new HtmlNode(tagName);
                parseAttributes(attributes, node);
                
                addNode(node, stack, roots);
                
                // Self-closing or void tags
                if (!isSelfClosing.isEmpty() || isVoidTag(tagName)) {
                    // Don't push to stack
                } else {
                    stack.add(node);
                }
            }
            
            lastEnd = matcher.end();
        }
        
        // Add remaining text
        if (lastEnd < html.length()) {
            String text = html.substring(lastEnd);
            if (!text.trim().isEmpty()) {
                HtmlNode textNode = new HtmlNode("text");
                textNode.text = unescapeHtml(text);
                addNode(textNode, stack, roots);
            }
        }
        
        return roots;
    }
    
    private static void addNode(HtmlNode node, List<HtmlNode> stack, List<HtmlNode> roots) {
        if (stack.isEmpty()) {
            roots.add(node);
        } else {
            stack.get(stack.size() - 1).children.add(node);
        }
    }
    
    private static void parseAttributes(String attrString, HtmlNode node) {
        if (attrString == null || attrString.trim().isEmpty()) return;
        
        Matcher matcher = ATTR_PATTERN.matcher(attrString);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase();
            String value = matcher.group(2);
            node.attributes.put(name, value);
        }
    }
    
    private static boolean isVoidTag(String tag) {
        return tag.equals("br") || tag.equals("img") || tag.equals("hr") || 
               tag.equals("input") || tag.equals("meta") || tag.equals("link");
    }
    
    private static String unescapeHtml(String text) {
        return text.replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&nbsp;", " ");
    }
    
    /**
     * Convert HTML node to LayoutSegment
     */
    private static LayoutSegment convertNode(HtmlNode node) {
        switch (node.tag) {
            case "text":
                return createTextSegment(node.text);
            
            case "p":
                return createParagraph(node);
            
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                return createHeading(node);
            
            case "strong":
            case "b":
                return createInlineFormatted(node, true, false);
            
            case "em":
            case "i":
                return createInlineFormatted(node, false, true);
            
            case "a":
                return createLink(node);
            
            case "img":
                return createImage(node);
            
            case "br":
                return createLineBreak();
            
            case "table":
                return createTable(node);
            
            case "div":
            case "span":
                return createContainer(node);
            
            default:
                // Unknown tag - treat as container
                return createContainer(node);
        }
    }
    
    private static LayoutSegment createTextSegment(String text) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.setTextContent(text);
        segment.getLayout().display = LayoutSegment.Display.INLINE;
        return segment;
    }
    
    private static LayoutSegment createParagraph(HtmlNode node) {
        if (node.children.isEmpty()) {
            // Empty paragraph
            LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
            segment.setTextContent("");
            segment.getLayout().display = LayoutSegment.Display.BLOCK;
            segment.getLayout().margin = new Insets(0, 0, 10, 0);
            return segment;
        }
        
        // Build rich text from children
        return buildRichTextSegment(node, LayoutSegment.Display.BLOCK, new Insets(0, 0, 10, 0));
    }
    
    private static LayoutSegment createHeading(HtmlNode node) {
        int level = Integer.parseInt(node.tag.substring(1)); // h1 -> 1, h2 -> 2, etc.
        int fontSize = 24 - (level - 1) * 2; // h1=24, h2=22, h3=20, etc.
        
        LayoutSegment segment = buildRichTextSegment(node, LayoutSegment.Display.BLOCK, new Insets(0, 0, 15, 0));
        segment.getStyle().fontSize = fontSize;
        segment.getStyle().bold = true;
        
        return segment;
    }
    
    private static LayoutSegment createInlineFormatted(HtmlNode node, boolean bold, boolean italic) {
        LayoutSegment segment = buildRichTextSegment(node, LayoutSegment.Display.INLINE, new Insets(0, 0, 0, 0));
        segment.getStyle().bold = bold;
        segment.getStyle().italic = italic;
        return segment;
    }
    
    private static LayoutSegment createLink(HtmlNode node) {
        String href = node.attributes.get("href");
        String title = node.attributes.get("title");
        
        LayoutSegment segment = buildRichTextSegment(node, LayoutSegment.Display.INLINE, new Insets(0, 0, 0, 0));
        
        if (href != null) {
            LinkProperties linkProps = new LinkProperties(href, title != null ? title : "");
            segment.setLinkProperties(linkProps);
            
            // Style links
            segment.getStyle().textColor = new Color(0, 0, 238); // Blue
            // Could add underline support here
        }
        
        return segment;
    }
    
    private static LayoutSegment createImage(HtmlNode node) {
        String src = node.attributes.get("src");
        //String alt = node.attributes.get("alt");
        String widthStr = node.attributes.get("width");
        String heightStr = node.attributes.get("height");
        
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.IMAGE);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 10, 0);
        
        // Try to load image from URL or base64
        if (src != null) {
            try {
                if (src.startsWith("data:")) {
                    // Base64 data URL
                    loadBase64Image(segment, src);
                } else {
                    // External URL
                    loadExternalImage(segment, src);
                }
            } catch (Exception e) {
                System.err.println("Failed to load image: " + src + " - " + e.getMessage());
            }
        }
        
        // Set dimensions if provided
        if (widthStr != null) {
            try {
                int width = Integer.parseInt(widthStr);
                segment.getLayout().width = LayoutSegment.Dimension.px(width);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        if (heightStr != null) {
            try {
                int height = Integer.parseInt(heightStr);
                segment.getLayout().height = LayoutSegment.Dimension.px(height);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return segment;
    }
    
    private static void loadBase64Image(LayoutSegment segment, String dataUrl) {
        // Parse data URL: data:image/png;base64,iVBORw0KG...
        CompletableFuture.runAsync(()->{
            String[] parts = dataUrl.split(",", 2);
            if (parts.length == 2) {
                String base64 = parts[1];
                byte[] imageData = java.util.Base64.getDecoder().decode(base64);
                NoteBytesImage img = new NoteBytesImage(imageData);
                TaskUtils.noDelay(_->{
                    segment.setImageContent(img);
                    segment.markDirty();
                });
            }
        });
    }
    
    private static void loadExternalImage(LayoutSegment segment, String urlString) {
        // Load image asynchronously to avoid blocking
        UrlStreamHelpers.getUrlBytes(urlString, TaskUtils.getVirtualExecutor())
            .thenAccept(imageData -> {
                if (imageData != null && imageData.length > 0) {
                    NoteBytesImage img = new NoteBytesImage(imageData);
                    TaskUtils.noDelay(_->{
                        segment.setImageContent(img);
                        segment.markDirty();
                    });
                }
            })
            .exceptionally(e -> {
                System.err.println("Failed to load external image: " + urlString + " - " + e.getMessage());
                return null;
            });
    }
    
    private static LayoutSegment createLineBreak() {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.setTextContent("\n");
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        return segment;
    }
    
    private static LayoutSegment createContainer(HtmlNode node) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
        segment.getLayout().display = node.tag.equals("div") ? 
            LayoutSegment.Display.BLOCK : LayoutSegment.Display.INLINE;
        
        // Check for grid layout attributes
        String gridCols = node.attributes.get("data-grid-columns");
        String gridRows = node.attributes.get("data-grid-rows");
        String gridGap = node.attributes.get("data-grid-gap");
        String resizableCols = node.attributes.get("data-resizable-columns");
        String resizableRows = node.attributes.get("data-resizable-rows");
        
        boolean hasGrid = gridCols != null || gridRows != null;
        
        if (hasGrid) {
            GridLayoutProperties gridProps = new GridLayoutProperties();
            gridProps.direction = GridLayoutProperties.Direction.ROW;
            
            // Parse columns (e.g., "1fr 2fr 1fr" or "200px auto 1fr")
            if (gridCols != null) {
                gridProps.columns = parseTrackSizes(gridCols);
            }
            
            // Parse rows
            if (gridRows != null) {
                gridProps.rows = parseTrackSizes(gridRows);
            }
            
            // Parse gap (e.g., "10" or "10 20" for row/column gap)
            if (gridGap != null) {
                String[] gaps = gridGap.trim().split("\\s+");
                if (gaps.length == 1) {
                    try {
                        int gap = Integer.parseInt(gaps[0]);
                        gridProps.gap = new GridLayoutProperties.Gap(gap);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                } else if (gaps.length == 2) {
                    try {
                        int rowGap = Integer.parseInt(gaps[0]);
                        int colGap = Integer.parseInt(gaps[1]);
                        gridProps.gap = new GridLayoutProperties.Gap(rowGap, colGap);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
            
            // Parse resizable flags
            if (resizableCols != null) {
                gridProps.resizableColumns = resizableCols.equals("true") || resizableCols.equals("1");
            }
            
            if (resizableRows != null) {
                gridProps.resizableRows = resizableRows.equals("true") || resizableRows.equals("1");
            }
            
            segment.setGridLayout(gridProps);
        }
        
        // Convert children

        for (HtmlNode child : node.children) {
            LayoutSegment childSegment = convertNode(child);
            if (childSegment != null) {
                // Check for grid item positioning attributes
                if (hasGrid) {
                    String gridCol = child.attributes.get("data-grid-column");
                    String gridRow = child.attributes.get("data-grid-row");
                    String gridColSpan = child.attributes.get("data-grid-column-span");
                    String gridRowSpan = child.attributes.get("data-grid-row-span");
                    
                    if (gridCol != null || gridRow != null || gridColSpan != null || gridRowSpan != null) {
                        GridItemProperties itemProps = new GridItemProperties();
                        
                        if (gridCol != null) {
                            try {
                                itemProps.column = Integer.parseInt(gridCol);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                        
                        if (gridRow != null) {
                            try {
                                itemProps.row = Integer.parseInt(gridRow);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                        
                        if (gridColSpan != null) {
                            try {
                                itemProps.columnSpan = Integer.parseInt(gridColSpan);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                        
                        if (gridRowSpan != null) {
                            try {
                                itemProps.rowSpan = Integer.parseInt(gridRowSpan);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                        
                        childSegment.setGridItem(itemProps);
                    }
                }
                
                segment.addChild(childSegment);
              
            }
        }
        
        return segment;
    }
    
    /**
     * Build a rich text segment from a node with inline children
     */
    private static LayoutSegment buildRichTextSegment(HtmlNode node, LayoutSegment.Display display, Insets margin) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getLayout().display = display;
        segment.getLayout().margin = margin;
        
        // Extract text and spans
        StringBuilder textBuilder = new StringBuilder();
        List<RichTextSpan> spans = new ArrayList<>();
        
        extractTextAndSpans(node, textBuilder, spans, segment.getStyle());
        
        segment.setTextContent(textBuilder.toString());
        segment.setTextSpans(spans);
        
        return segment;
    }
    
    /**
     * Recursively extract text and formatting spans from node tree
     */
    private static void extractTextAndSpans(HtmlNode node, StringBuilder text, 
                                           List<RichTextSpan> spans, 
                                           LayoutSegment.StyleProperties baseStyle) {
        for (HtmlNode child : node.children) {
            if (child.tag.equals("text")) {
                text.append(child.text);
            } else {
                int startPos = text.length();
                
                // Create style for this span
                LayoutSegment.StyleProperties spanStyle = new LayoutSegment.StyleProperties();
                spanStyle.fontName = baseStyle.fontName;
                spanStyle.fontSize = baseStyle.fontSize;
                spanStyle.bold = baseStyle.bold;
                spanStyle.italic = baseStyle.italic;
                spanStyle.textColor = baseStyle.textColor;
                
                // Apply formatting
                if (child.tag.equals("strong") || child.tag.equals("b")) {
                    spanStyle.bold = true;
                } else if (child.tag.equals("em") || child.tag.equals("i")) {
                    spanStyle.italic = true;
                } else if (child.tag.equals("a")) {
                    spanStyle.textColor = new Color(0, 0, 238);
                }
                
                // Recursively extract child text
                extractTextAndSpans(child, text, spans, spanStyle);
                
                int endPos = text.length();
                
                // Only create span if formatting differs from base
                if (endPos > startPos && (spanStyle.bold != baseStyle.bold || 
                                         spanStyle.italic != baseStyle.italic ||
                                         !spanStyle.textColor.equals(baseStyle.textColor))) {
                    spans.add(new RichTextSpan(startPos, endPos, spanStyle));
                }
            }
        }
    }
    
    /**
     * Create a grid layout from an HTML table
     */
    private static LayoutSegment createTable(HtmlNode tableNode) {
        LayoutSegment container = new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
        container.getLayout().display = LayoutSegment.Display.BLOCK;
        container.getLayout().margin = new Insets(0, 0, 10, 0);
        
        // Parse table structure
        List<List<HtmlNode>> rows = new ArrayList<>();
        int maxColumns = 0;
        
        // Find tbody or use table directly
        List<HtmlNode> bodyNodes = findChildrenByTag(tableNode, "tbody");
        List<HtmlNode> rowNodes = bodyNodes.isEmpty() ? 
            findChildrenByTag(tableNode, "tr") : 
            findChildrenByTag(bodyNodes.get(0), "tr");
        
        // Also check for thead
        List<HtmlNode> theadNodes = findChildrenByTag(tableNode, "thead");
        if (!theadNodes.isEmpty()) {
            List<HtmlNode> headerRows = findChildrenByTag(theadNodes.get(0), "tr");
            rowNodes.addAll(0, headerRows);
        }
        
        for (HtmlNode rowNode : rowNodes) {
            List<HtmlNode> cells = new ArrayList<>();
            cells.addAll(findChildrenByTag(rowNode, "th"));
            cells.addAll(findChildrenByTag(rowNode, "td"));
            rows.add(cells);
            maxColumns = Math.max(maxColumns, cells.size());
        }
        
        if (rows.isEmpty() || maxColumns == 0) {
            return container; // Empty table
        }
        
        // Create grid layout properties
        GridLayoutProperties gridProps = new GridLayoutProperties();
        gridProps.direction = GridLayoutProperties.Direction.ROW;
        
        // Define columns - use auto sizing for content
        for (int i = 0; i < maxColumns; i++) {
            gridProps.columns.add(GridLayoutProperties.TrackSize.fr(1.0));
        }
        
        // Define rows - use auto sizing
        for (int i = 0; i < rows.size(); i++) {
            gridProps.rows.add(GridLayoutProperties.TrackSize.auto());
        }
        
        // Add gap between cells
        gridProps.gap = new GridLayoutProperties.Gap(5, 5);
        
        // Enable resizing if table has border attribute
        String border = tableNode.attributes.get("border");
        boolean hasBorder = border != null && !border.equals("0");
        gridProps.resizableColumns = hasBorder;
        gridProps.resizableRows = false; // Usually don't resize table rows
        
        container.setGridLayout(gridProps);
        
        // Convert cells to segments
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            List<HtmlNode> cellNodes = rows.get(rowIdx);
            
            for (int colIdx = 0; colIdx < cellNodes.size(); colIdx++) {
                HtmlNode cellNode = cellNodes.get(colIdx);
                
                // Create cell container
                LayoutSegment cell = new LayoutSegment(LayoutSegment.SegmentType.CONTAINER);
                cell.getLayout().display = LayoutSegment.Display.BLOCK;
                cell.getLayout().padding = new Insets(5, 5, 5, 5);
                
                // Add border to cells
                if (hasBorder) {
                    cell.getLayout().borderWidth = 1;
                    cell.getLayout().borderColor = Color.LIGHT_GRAY;
                }
                
                // Check for colspan/rowspan
                String colspanStr = cellNode.attributes.get("colspan");
                String rowspanStr = cellNode.attributes.get("rowspan");
                
                int colspan = 1;
                int rowspan = 1;
                
                if (colspanStr != null) {
                    try {
                        colspan = Integer.parseInt(colspanStr);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                
                if (rowspanStr != null) {
                    try {
                        rowspan = Integer.parseInt(rowspanStr);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                
                // Set grid position
                GridItemProperties itemProps = new GridItemProperties();
                itemProps.column = colIdx + 1; // Grid positions are 1-based
                itemProps.row = rowIdx + 1;
                itemProps.columnSpan = colspan;
                itemProps.rowSpan = rowspan;
                cell.setGridItem(itemProps);
                
                // Special styling for header cells (th)
                boolean isHeader = cellNode.tag.equals("th");
                if (isHeader) {
                    cell.getLayout().backgroundColor = new Color(240, 240, 240);
                }
                
                // Convert cell content
                for (HtmlNode contentNode : cellNode.children) {
                    LayoutSegment contentSegment = convertNode(contentNode);
                    if (contentSegment != null) {
                        // Header cells should be bold
                        if (isHeader && contentSegment.getType() == LayoutSegment.SegmentType.TEXT) {
                            contentSegment.getStyle().bold = true;
                        }
                        cell.addChild(contentSegment);
                    }
                }
                
                // If cell is empty, add empty text segment
                if (cell.getChildren().size() == 0) {
                    LayoutSegment emptyText = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
                    emptyText.setTextContent("");
                    emptyText.getLayout().display = LayoutSegment.Display.INLINE;
                    cell.addChild(emptyText);
                }
                
                container.addChild(cell);
            }
        }
        
        return container;
    }
    
    /**
     * Find all direct children with a specific tag name
     */
    private static List<HtmlNode> findChildrenByTag(HtmlNode parent, String tag) {
        List<HtmlNode> result = new ArrayList<>();
        for (HtmlNode child : parent.children) {
            if (child.tag.equals(tag)) {
                result.add(child);
            }
        }
        return result;
    }
    
    /**
     * Parse track sizes from CSS-like string
     * Examples: "1fr 2fr 1fr", "200px auto 1fr", "repeat(3, 1fr)"
     */
    private static List<GridLayoutProperties.TrackSize> parseTrackSizes(String input) {
        List<GridLayoutProperties.TrackSize> tracks = new ArrayList<>();
        
        if (input == null || input.trim().isEmpty()) {
            return tracks;
        }
        
        // Handle repeat() function
        Pattern repeatPattern = Pattern.compile("repeat\\((\\d+),\\s*([^)]+)\\)");
        Matcher repeatMatcher = repeatPattern.matcher(input);
        
        if (repeatMatcher.find()) {
            int count = Integer.parseInt(repeatMatcher.group(1));
            String trackDef = repeatMatcher.group(2).trim();
            
            for (int i = 0; i < count; i++) {
                tracks.add(parseTrackSize(trackDef));
            }
            
            return tracks;
        }
        
        // Parse individual track sizes
        String[] parts = input.trim().split("\\s+");
        for (String part : parts) {
            tracks.add(parseTrackSize(part));
        }
        
        return tracks;
    }
    
    /**
     * Parse a single track size
     * Examples: "200px", "50%", "1fr", "auto", "min-content", "max-content"
     */
    private static GridLayoutProperties.TrackSize parseTrackSize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return GridLayoutProperties.TrackSize.auto();
        }
        
        input = input.trim().toLowerCase();
        
        if (input.equals("auto")) {
            return GridLayoutProperties.TrackSize.auto();
        }
        
        if (input.equals("min-content")) {
            return GridLayoutProperties.TrackSize.minContent();
        }
        
        if (input.equals("max-content")) {
            return GridLayoutProperties.TrackSize.maxContent();
        }
        
        if (input.endsWith("px")) {
            try {
                double value = Double.parseDouble(input.substring(0, input.length() - 2));
                return GridLayoutProperties.TrackSize.px(value);
            } catch (NumberFormatException e) {
                return GridLayoutProperties.TrackSize.auto();
            }
        }
        
        if (input.endsWith("%")) {
            try {
                double value = Double.parseDouble(input.substring(0, input.length() - 1));
                return GridLayoutProperties.TrackSize.percent(value);
            } catch (NumberFormatException e) {
                return GridLayoutProperties.TrackSize.auto();
            }
        }
        
        if (input.endsWith("fr")) {
            try {
                double value = Double.parseDouble(input.substring(0, input.length() - 2));
                return GridLayoutProperties.TrackSize.fr(value);
            } catch (NumberFormatException e) {
                return GridLayoutProperties.TrackSize.auto();
            }
        }
        
        return GridLayoutProperties.TrackSize.auto();
    }
    
    /**
     * Simple HTML node representation
     */
    private static class HtmlNode {
        String tag;
        String text = "";
        Map<String, String> attributes = new HashMap<>();
        List<HtmlNode> children = new ArrayList<>();
        
        HtmlNode(String tag) {
            this.tag = tag;
        }
    }
}