package io.netnotes.gui.fx.components.layout;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.gui.fx.utils.TaskUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Markdown to LayoutSegment structures.
 * Supports: headings, bold, italic, links, images, lists, code blocks
 */
public class MarkdownToSegmentBuilder {
    
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+?)\\*(?!\\*)|(?<!_)_([^_]+?)_(?!_)");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern LIST_PATTERN = Pattern.compile("^[*+-]\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+\\.\\s+(.+)$");
    
    /**
     * Parse Markdown string into segments
     */
    public static NoteBytesArray buildFromMarkdown(String markdown) {
        NoteBytesArray segments = new NoteBytesArray();
        
        if (markdown == null || markdown.trim().isEmpty()) {
            return segments;
        }
        
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Code blocks
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // End of code block
                    segments.add(createCodeBlock(codeBlockContent.toString()).getData());
                    codeBlockContent = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    // Start of code block
                    inCodeBlock = true;
                }
                continue;
            }
            
            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n");
                continue;
            }
            
            // Empty line
            if (line.trim().isEmpty()) {
                continue;
            }
            
            // Check for standalone images (must come before links since images contain link syntax)
            Matcher imageMatcher = IMAGE_PATTERN.matcher(line.trim());
            if (imageMatcher.matches()) {
                String alt = imageMatcher.group(1);
                String src = imageMatcher.group(2);
                segments.add(createImage(src, alt).getData());
                continue;
            }
            
            // Headings
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                String text = headingMatcher.group(2);
                segments.add(createHeading(text, level).getData());
                continue;
            }
            
            // Lists
            Matcher listMatcher = LIST_PATTERN.matcher(line);
            if (listMatcher.matches()) {
                String text = listMatcher.group(1);
                segments.add(createListItem(text, false).getData());
                continue;
            }
            
            Matcher orderedListMatcher = ORDERED_LIST_PATTERN.matcher(line);
            if (orderedListMatcher.matches()) {
                String text = orderedListMatcher.group(1);
                segments.add(createListItem(text, true).getData());
                continue;
            }
            
            // Regular paragraph
            segments.add(createParagraph(line).getData());
        }
        
        return segments;
    }
    
    /**
     * Create a paragraph with inline formatting
     */
    private static LayoutSegment createParagraph(String text) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 10, 0);
        
        // Parse inline formatting
        InlineParseResult result = parseInlineFormatting(text);
        segment.setTextContent(result.text);
        segment.setTextSpans(result.spans);
        
        // Check if paragraph contains a link
        if (result.linkUrl != null) {
            segment.setLinkProperties(new LinkProperties(result.linkUrl, ""));
        }
        
        return segment;
    }
    
    /**
     * Create a heading
     */
    private static LayoutSegment createHeading(String text, int level) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 15, 0);
        
        // Set font size based on level
        int fontSize = 24 - (level - 1) * 2; // h1=24, h2=22, h3=20, etc.
        segment.getStyle().fontSize = fontSize;
        segment.getStyle().bold = true;
        
        // Parse inline formatting
        InlineParseResult result = parseInlineFormatting(text);
        segment.setTextContent(result.text);
        segment.setTextSpans(result.spans);
        
        return segment;
    }
    
    /**
     * Create a list item
     */
    private static LayoutSegment createListItem(String text, boolean ordered) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 5, 0);
        segment.getLayout().padding = new Insets(0, 0, 0, 20);
        
        // Add bullet/number prefix
        String prefix = ordered ? "1. " : "• ";
        
        // Parse inline formatting
        InlineParseResult result = parseInlineFormatting(text);
        segment.setTextContent(prefix + result.text);
        
        // Adjust span positions for prefix
        List<RichTextSpan> adjustedSpans = new ArrayList<>();
        for (RichTextSpan span : result.spans) {
            RichTextSpan adjusted = span.copy();
            adjusted.setStart(span.getStart() + prefix.length());
            adjusted.setEnd(span.getEnd() + prefix.length());
            adjustedSpans.add(adjusted);
        }
        segment.setTextSpans(adjustedSpans);
        
        return segment;
    }
    
    /**
     * Create a code block
     */
    private static LayoutSegment createCodeBlock(String code) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.setTextContent(code);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 10, 0);
        segment.getLayout().padding = new Insets(10, 10, 10, 10);
        segment.getLayout().backgroundColor = new Color(245, 245, 245);
        segment.getStyle().fontName = "Monospaced";
        segment.getStyle().fontSize = 12;
        
        return segment;
    }
    
    /**
     * Create an image segment from markdown
     */
    private static LayoutSegment createImage(String src, String alt) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.IMAGE);
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 10, 0);
        
        // Try to load image from URL or base64
        if (src != null && !src.isEmpty()) {
            try {
                if (src.startsWith("data:")) {
                    // Base64 data URL
                    loadBase64Image(segment, src);
                } else {
                    // External URL or file path
                    loadExternalImage(segment, src);
                }
            } catch (Exception e) {
                System.err.println("Failed to load image: " + src + " - " + e.getMessage());
                // Create placeholder text segment instead
                return createImagePlaceholder(alt != null ? alt : src);
            }
        }
        
        // Default to auto sizing (will use intrinsic dimensions or aspect ratio)
        segment.getLayout().width = LayoutSegment.Dimension.auto();
        segment.getLayout().height = LayoutSegment.Dimension.auto();
        
        return segment;
    }
    
    private static void loadBase64Image(LayoutSegment segment, String dataUrl) {
        // Parse data URL: data:image/png;base64,iVBORw0KG...
        String[] parts = dataUrl.split(",", 2);
        if (parts.length == 2) {
            String base64 = parts[1];
            byte[] imageData = java.util.Base64.getDecoder().decode(base64);
            segment.setBinaryContent(imageData);
        }
    }
    
    private static void loadExternalImage(LayoutSegment segment, String urlString) {
        // Load image asynchronously to avoid blocking
        UrlStreamHelpers.getUrlBytes(urlString, TaskUtils.getVirtualExecutor())
            .thenAccept(imageData -> {
                if (imageData != null && imageData.length > 0) {
                    segment.setBinaryContent(imageData);
                    segment.markDirty();
                }
            })
            .exceptionally(e -> {
                System.err.println("Failed to load external image: " + urlString + " - " + e.getMessage());
                return null;
            });
    }
    
    private static LayoutSegment createImagePlaceholder(String text) {
        LayoutSegment segment = new LayoutSegment(LayoutSegment.SegmentType.TEXT);
        segment.setTextContent("[Image: " + text + "]");
        segment.getLayout().display = LayoutSegment.Display.BLOCK;
        segment.getLayout().margin = new Insets(0, 0, 10, 0);
        segment.getLayout().padding = new Insets(5, 5, 5, 5);
        segment.getLayout().backgroundColor = new Color(240, 240, 240);
        segment.getStyle().textColor = Color.GRAY;
        segment.getStyle().italic = true;
        return segment;
    }
    
    /**
     * Parse inline formatting (bold, italic, links, code, images)
     */
    private static InlineParseResult parseInlineFormatting(String text) {
        StringBuilder cleanText = new StringBuilder();
        List<RichTextSpan> spans = new ArrayList<>();
        List<InlineToken> tokens = tokenizeInline(text);
        
        int currentPos = 0;
        String linkUrl = null;
        
        for (InlineToken token : tokens) {
            if (token.type == TokenType.TEXT) {
                cleanText.append(token.text);
                currentPos += token.text.length();
            } else if (token.type == TokenType.IMAGE) {
                // Inline images in text are converted to placeholder text
                String placeholder = "[Image: " + (token.text.isEmpty() ? token.url : token.text) + "]";
                int startPos = currentPos;
                cleanText.append(placeholder);
                currentPos += placeholder.length();
                
                // Style as italic gray
                LayoutSegment.StyleProperties style = new LayoutSegment.StyleProperties();
                style.italic = true;
                style.textColor = Color.GRAY;
                spans.add(new RichTextSpan(startPos, currentPos, style));
            } else {
                int startPos = currentPos;
                cleanText.append(token.text);
                int endPos = currentPos + token.text.length();
                currentPos = endPos;
                
                // Create span based on token type
                LayoutSegment.StyleProperties style = new LayoutSegment.StyleProperties();
                
                switch (token.type) {
                    case BOLD:
                        style.bold = true;
                        spans.add(new RichTextSpan(startPos, endPos, style));
                        break;
                    case ITALIC:
                        style.italic = true;
                        spans.add(new RichTextSpan(startPos, endPos, style));
                        break;
                    case CODE:
                        style.fontName = "Monospaced";
                        style.textColor = new Color(200, 50, 50);
                        spans.add(new RichTextSpan(startPos, endPos, style));
                        break;
                    case LINK:
                        style.textColor = new Color(0, 0, 238);
                        spans.add(new RichTextSpan(startPos, endPos, style));
                        if (linkUrl == null) {
                            linkUrl = token.url; // Store first link URL
                        }
                        break;
                    case TEXT:
                        // Plain text, no special formatting needed
                        break;
                    case IMAGE:
                        // Images handled separately above
                        break;
                }
            }
        }
        
        return new InlineParseResult(cleanText.toString(), spans, linkUrl);
    }
    
    /**
     * Tokenize inline markdown elements
     */
    private static List<InlineToken> tokenizeInline(String text) {
        List<InlineToken> tokens = new ArrayList<>();
        int pos = 0;
        
        while (pos < text.length()) {
            // Try to match patterns at current position
            InlineToken token = null;
            
            // Images (must come before links)
            Matcher imageMatcher = IMAGE_PATTERN.matcher(text.substring(pos));
            if (imageMatcher.lookingAt()) {
                String altText = imageMatcher.group(1);
                String url = imageMatcher.group(2);
                token = new InlineToken(TokenType.IMAGE, altText, url);
                pos += imageMatcher.end();
            }
            
            // Links
            if (token == null) {
                Matcher linkMatcher = LINK_PATTERN.matcher(text.substring(pos));
                if (linkMatcher.lookingAt()) {
                    String linkText = linkMatcher.group(1);
                    String url = linkMatcher.group(2);
                    token = new InlineToken(TokenType.LINK, linkText, url);
                    pos += linkMatcher.end();
                }
            }
            
            // Bold
            if (token == null) {
                Matcher boldMatcher = BOLD_PATTERN.matcher(text.substring(pos));
                if (boldMatcher.lookingAt()) {
                    String content = boldMatcher.group(1) != null ? boldMatcher.group(1) : boldMatcher.group(2);
                    token = new InlineToken(TokenType.BOLD, content);
                    pos += boldMatcher.end();
                }
            }
            
            // Italic (check if not part of bold)
            if (token == null) {
                Matcher italicMatcher = ITALIC_PATTERN.matcher(text.substring(pos));
                if (italicMatcher.lookingAt()) {
                    String content = italicMatcher.group(1) != null ? italicMatcher.group(1) : italicMatcher.group(2);
                    token = new InlineToken(TokenType.ITALIC, content);
                    pos += italicMatcher.end();
                }
            }
            
            // Code
            if (token == null) {
                Matcher codeMatcher = CODE_PATTERN.matcher(text.substring(pos));
                if (codeMatcher.lookingAt()) {
                    String content = codeMatcher.group(1);
                    token = new InlineToken(TokenType.CODE, content);
                    pos += codeMatcher.end();
                }
            }
            
            // Plain text
            if (token == null) {
                // Find next special character
                int nextSpecial = findNextSpecial(text, pos);
                String plainText = text.substring(pos, nextSpecial);
                token = new InlineToken(TokenType.TEXT, plainText);
                pos = nextSpecial;
            }
            
            tokens.add(token);
        }
        
        return tokens;
    }
    
    private static int findNextSpecial(String text, int start) {
        int pos = start + 1;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '*' || c == '_' || c == '`' || c == '[' || c == '!') {
                return pos;
            }
            pos++;
        }
        return text.length();
    }
    
    /**
     * Token types for inline markdown
     */
    private enum TokenType {
        TEXT, BOLD, ITALIC, CODE, LINK, IMAGE
    }
    
    /**
     * Inline token
     */
    private static class InlineToken {
        TokenType type;
        String text;
        String url; // For links and images
        
        InlineToken(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
        
        InlineToken(TokenType type, String text, String url) {
            this.type = type;
            this.text = text;
            this.url = url;
        }
    }
    
    /**
     * Result of inline parsing
     */
    private static class InlineParseResult {
        String text;
        List<RichTextSpan> spans;
        String linkUrl; // First link URL found (for segments that are entirely a link)
        
        /*InlineParseResult(String text, List<RichTextSpan> spans) {
            this(text, spans, null);
        }*/
        
        InlineParseResult(String text, List<RichTextSpan> spans, String linkUrl) {
            this.text = text;
            this.spans = spans;
            this.linkUrl = linkUrl;
        }
    }
}