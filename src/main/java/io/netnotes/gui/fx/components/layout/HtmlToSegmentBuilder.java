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
 * Supports basic HTML tags: p, h1-h6, strong, b, em, i, a, img, div, span, br
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
        
        // Convert children
        for (HtmlNode child : node.children) {
            LayoutSegment childSegment = convertNode(child);
            if (childSegment != null) {
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