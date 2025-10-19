package io.netnotes.gui.fx.display;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

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
        return getCharacterSizeSync("OCR A Extended", java.awt.Font.PLAIN, fontSize);
    }

    public int getCharacterSizeSync(String font, int fontStyle, int fontSize){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font(font, fontStyle, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.charWidth(' ');
        dispose();

        return width;
    }

    public void dispose(){
        m_tmpFm = null;
        m_tmpG2d.dispose();
        m_tmpG2d = null;
        m_tmpFont = null;
        m_tmpImg = null;
    }

    public int getStringWidthSync(String str, int fontSize, String fontName, int fontStyle){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font(fontName, fontStyle, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.stringWidth(str);

        dispose();

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

    
      
    public Text getHelper() {
        return m_helper;
    }

    public void setHelper(Text m_helper) {
        this.m_helper = m_helper;
    }

    public BufferedImage getTmpImg() {
        return m_tmpImg;
    }

    public void setTmpImg(BufferedImage m_tmpImg) {
        this.m_tmpImg = m_tmpImg;
    }

    public Graphics2D getTmpG2d() {
        return m_tmpG2d;
    }

    public void setTmpG2d(Graphics2D m_tmpG2d) {
        this.m_tmpG2d = m_tmpG2d;
    }

    public java.awt.Font getTmpFont() {
        return m_tmpFont;
    }

    public void setTmpFont(java.awt.Font tmpFont) {
        this.m_tmpFont = tmpFont;
    }

    public FontMetrics getTmpFontMetrics() {
        return m_tmpFm;
    }

    public void setTmpFontMetrics(FontMetrics m_tmpFm) {
        this.m_tmpFm = m_tmpFm;
    }
    
}
