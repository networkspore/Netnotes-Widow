package io.netnotes.gui.fx.display;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class TextHelper {
    private Text m_helper = null;
    private BufferedImage m_tmpImg = null;
    private Graphics2D m_tmpG2d = null;
    private java.awt.Font m_tmpFont = null;
    private FontMetrics m_tmpFm = null;

    public double computeTextWidthSync(Font font, String text, double wrappingWidth) {
    
        m_helper = new Text();
        m_helper.setFont(font);
        m_helper.setText(text);
        // Note that the wrapping width needs to be set to zero before
        // getting the text's real preferred width.
        m_helper.setWrappingWidth(0);
        m_helper.setLineSpacing(0);
        double w = Math.min(m_helper.prefWidth(-1), wrappingWidth);
        m_helper.setWrappingWidth((int)Math.ceil(w));
        double textWidth = Math.ceil(m_helper.getLayoutBounds().getWidth());
        m_helper = null;
        return textWidth;
    }

    public double computeTextWidthSync(Font font, String text) {
        m_helper = new Text();
        m_helper.setFont(font);
        m_helper.setText(text);
        double w =  Math.ceil(m_helper.getLayoutBounds().getWidth());
        m_helper = null;
        return w;
    }


    public int getCharacterSizeSync(int fontSize){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font("OCR A Extended", java.awt.Font.PLAIN, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.charWidth(' ');
        m_tmpFm = null;
        m_tmpG2d.dispose();
        m_tmpG2d = null;
        m_tmpFont = null;

        m_tmpImg = null;

        return width;
    }

    public int getStringWidthSync(String str, int fontSize, String fontName, int fontStyle){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font(fontName, fontStyle, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.stringWidth(str);

        m_tmpFm = null;
        m_tmpG2d.dispose();
        m_tmpG2d = null;
        m_tmpFont = null;

        m_tmpImg = null;

        return width;
    }

    public int getStringWidthSync(String str){
        return getStringWidthSync(str, 14);
    }

    public int getStringWidthSync(String str, int fontSize){
        return getStringWidthSync(str, fontSize, "OCR A Extended", java.awt.Font.PLAIN);
    }


    public static String truncateText(String text,FontMetrics metrics, double width) {
       
        String truncatedString = text.substring(0, 5) + "..";
        if (text.length() > 3) {
            int i = text.length() - 3;
            truncatedString = text.substring(0, i) + "..";

            while (metrics.stringWidth(truncatedString) > width && i > 1) {
                i = i - 1;
                truncatedString = text.substring(0, i) + "..";

            }
        }
        return truncatedString;
    }

     public static Image getPosNegText(String text,java.awt.Color posColor, java.awt.Color posHighlightColor, java.awt.Color negColor, java.awt.Color negHeightlightColor,  boolean positive, boolean neutral ) {
     
        int height = 30;


        java.awt.Font font = new java.awt.Font("OCR A Extended", java.awt.Font.BOLD, 15);

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();

        int textWidth = fm.stringWidth(text);
        int fontAscent = fm.getAscent();
        int fontHeight = fm.getHeight();
        int stringY = ((height - fontHeight) / 2) + fontAscent;


        img = new BufferedImage(textWidth, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();

        g2d.setFont(font);


        if (neutral) {
            g2d.setColor(new java.awt.Color(0x777777));
            g2d.drawString(text, 0, stringY);

        } else {
            java.awt.Color fillColor = java.awt.Color.BLUE;
            g2d.setColor(fillColor);
            g2d.drawString(text, 0, stringY);

            int x1 = 0;
            int y1 = (height / 2) - (fontHeight / 2);
            int x2 = textWidth;
            int y2 = y1 + fontHeight;
            java.awt.Color color1 = positive ? posColor : negHeightlightColor;
            java.awt.Color color2 = positive ? posHighlightColor : negColor;

            ImageHelpers.drawBarFillColor(positive ? 0 : 1, false, fillColor.getRGB(), color1.getRGB(), color2.getRGB(), img, x1, y1, x2, y2);

        }

        g2d.dispose();

        return SwingFXUtils.toFXImage(img, null);
    }

}
