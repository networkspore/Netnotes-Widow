package io.netnotes.gui.fx.components.fields;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.gui.fx.components.notifications.Alerts;
import io.netnotes.gui.fx.display.FxResourceFactory;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

public class PassField extends Label implements AutoCloseable {
    public static final int MAX_PASSWORD_BYTE_LENGTH = 256;
    public static final int MAX_KEYSTROKE_COUNT = 128;
    public static final String FOCUSED_CHAR = "▮";
    public static final String UNFOCUSED_CHAR = "▯";

    private final Timeline blinkTimeline;

    private NoteBytesEphemeral m_passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
    private byte[] m_keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int m_currentLength = 0; // Total bytes used
    private int m_keystrokeCount = 0; // Number of keystrokes
    private Runnable m_onEscapeRunnable = null;
    private Consumer<PassField> m_onAction = null;
    private boolean m_cursorVisible = true;

    public PassField() {
        super();
        setFocusTraversable(true);
        focusedProperty().addListener((_, _, _) -> {
            updateDisplay();
        });

        // Handle control keys (backspace, navigation, etc.)
        setOnKeyPressed(this::handleControlKeys);
        // Handle actual character input
        setOnKeyTyped(this::handleCharacterInput);
        
        updateDisplay();

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.millis(FxResourceFactory.CURSOR_DELAY), _ -> toggleCursor()));
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);

         // focus control
        focusedProperty().addListener((_, _, isNowFocused) -> {
            if (isNowFocused) {
                blinkTimeline.play();
            } else {
                blinkTimeline.stop();
                setText(UNFOCUSED_CHAR);
            }
        });
    }

    private void toggleCursor() {
        if (isFocused()) {
            m_cursorVisible = !m_cursorVisible;
            updateDisplay();
        }else if(!m_cursorVisible){
            m_cursorVisible = true;
            updateDisplay();
        }
    }
    
    private void handleControlKeys(KeyEvent event) {
        KeyCode keyCode = event.getCode();
    

        blinkTimeline.playFromStart();
        if(keyCode == KeyCode.ENTER){
            fire();
        }if (keyCode == KeyCode.BACK_SPACE) {
            if (m_keystrokeCount > 0) {
                // Get the length of the last keystroke
                m_keystrokeCount--;
                int lastKeystrokeLength = m_keystrokeLengths[m_keystrokeCount];
                
                // Zero out the bytes of the last keystroke
                for (int i = 0; i < lastKeystrokeLength; i++) {
                    m_passwordBytes.get()[m_currentLength - lastKeystrokeLength + i] = 0;
                }
                
                m_currentLength -= lastKeystrokeLength;
                m_keystrokeLengths[m_keystrokeCount] = 0;
            }
       
            event.consume();
        } else if (keyCode == KeyCode.ESCAPE) {
            escape();
            event.consume();
              
        } else if (FxResourceFactory.KEY_COMB_CTL_U.match(event)) {
            // Ctrl+U: Unix-style clear line
            clear();
            event.consume();
        } else if (keyCode == KeyCode.ENTER || keyCode == KeyCode.TAB) {
            // Allow navigation keys to pass through
            // Don't consume these - let them handle focus traversal
        } else if (isModifierKey(keyCode) || isNavigationKey(keyCode) || isSystemKey(keyCode)) {
            // Consume but don't record these keys
            event.consume();
        }
        m_cursorVisible = true;
        updateDisplay();
    }

    public void fire(){
        if(m_onAction != null){
            m_onAction.accept(this);
        }
    }
    
    private void handleCharacterInput(KeyEvent event) {
        String character = event.getCharacter();
        
        // Filter out control characters (except for printable ones)
        if (character == null || character.isEmpty() || 
            character.charAt(0) < 32 || character.equals("\u007F")) {
            event.consume();
            return;
        }
        
        // Convert character to bytes
        byte[] charBytes = character.getBytes(StandardCharsets.UTF_8);
        
        // Check if we have room for this keystroke
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            Alerts.showAndWaitErrorAlert("Input Limit Reached", "Password cannot exceed " + MAX_KEYSTROKE_COUNT + " keystrokes.",
                getScene().getWindow(), ButtonType.OK);
         
            event.consume();
            return;
        }
        
        if (m_currentLength + charBytes.length > MAX_PASSWORD_BYTE_LENGTH) {
            Alerts.showAndWaitErrorAlert("Input Limit Reached", "Password size " + MAX_PASSWORD_BYTE_LENGTH + " reached.",
                getScene().getWindow(), ButtonType.OK);
            event.consume();
            return;
        }
        
        // Store the character bytes
        System.arraycopy(charBytes, 0, m_passwordBytes.get(), m_currentLength, charBytes.length);
        m_keystrokeLengths[m_keystrokeCount] = (byte) charBytes.length;
        m_currentLength += charBytes.length;
        m_keystrokeCount++;
        
        updateDisplay();
        event.consume();
    }

    public void setOnAction(Consumer<PassField> actionConsumer){
        m_onAction = actionConsumer;
    }

    public void escape(){
        // Clear all input
        clearInput();
        updateDisplay();
      
        if(m_onEscapeRunnable != null){
            m_onEscapeRunnable.run();
        }
    }
    
    private void clearInput() {
        m_passwordBytes.close();
        // Clear keystroke lengths
        for (int i = 0; i < m_keystrokeCount; i++) {
            m_keystrokeLengths[i] = 0;
        }
        m_currentLength = 0;
        m_keystrokeCount = 0;
    }
    
    private boolean isModifierKey(KeyCode keyCode) {
        return keyCode == KeyCode.SHIFT ||
               keyCode == KeyCode.CONTROL ||
               keyCode == KeyCode.ALT ||
               keyCode == KeyCode.ALT_GRAPH ||
               keyCode == KeyCode.META ||
               keyCode == KeyCode.COMMAND ||
               keyCode == KeyCode.WINDOWS ||
               keyCode == KeyCode.SHORTCUT;
    }
    
    private boolean isNavigationKey(KeyCode keyCode) {
        return keyCode == KeyCode.UP ||
               keyCode == KeyCode.DOWN ||
               keyCode == KeyCode.LEFT ||
               keyCode == KeyCode.RIGHT ||
               keyCode == KeyCode.HOME ||
               keyCode == KeyCode.END ||
               keyCode == KeyCode.PAGE_UP ||
               keyCode == KeyCode.PAGE_DOWN;
    }
    
    private boolean isSystemKey(KeyCode keyCode) {
        return keyCode == KeyCode.F1 || keyCode == KeyCode.F2 || keyCode == KeyCode.F3 ||
               keyCode == KeyCode.F4 || keyCode == KeyCode.F5 || keyCode == KeyCode.F6 ||
               keyCode == KeyCode.F7 || keyCode == KeyCode.F8 || keyCode == KeyCode.F9 ||
               keyCode == KeyCode.F10 || keyCode == KeyCode.F11 || keyCode == KeyCode.F12 ||
               keyCode == KeyCode.INSERT || keyCode == KeyCode.DELETE ||
               keyCode == KeyCode.PRINTSCREEN || keyCode == KeyCode.SCROLL_LOCK ||
               keyCode == KeyCode.PAUSE || keyCode == KeyCode.CAPS ||
               keyCode == KeyCode.NUM_LOCK || keyCode == KeyCode.CONTEXT_MENU;
    }
    
    private void updateDisplay() {
        if (!isFocused()) {
            setText(UNFOCUSED_CHAR);
            return;
        }
        if (m_cursorVisible) {
            setText(FOCUSED_CHAR);
        } else {
            setText(" "); // blank when off
        }
    }
    
    public NoteBytesEphemeral getEphemeralPassword() {
        return m_passwordBytes.copyOf(m_currentLength);
    }

   

    public void setOnEscape(Runnable onEscape){
        m_onEscapeRunnable = onEscape;
    }
    
    // Method to clear the password
    public void clear() {
        clearInput();
        updateDisplay();
    }
    
    // Destroys the password (close calls clear)
    @Override
    public void close() {
        escape();
    }
}