package io.netnotes.gui.fx.components;

import java.nio.charset.StandardCharsets;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.gui.fx.components.notifications.Alerts;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class PassField extends PasswordField implements AutoCloseable {
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int MAX_KEYSTROKE_COUNT = 128;
    private static final String FOCUSED_CHAR = "▮";
    private static final String UNFOCUSED_CHAR = "▯";
    
    private NoteBytesEphemeral passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_LENGTH]);
    private byte[] keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int currentLength = 0; // Total bytes used
    private int keystrokeCount = 0; // Number of keystrokes
    private Runnable onCloseRunnable = null;
    private Runnable onEscapeRunnable = null;
    
    public PassField() {
        super();
        setEditable(false);
        setFocusTraversable(true);
        focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateDisplay();
        });
        
        // Handle control keys (backspace, navigation, etc.)
        setOnKeyPressed(this::handleControlKeys);
        // Handle actual character input
        setOnKeyTyped(this::handleCharacterInput);
        
        updateDisplay();
    }
    
    private void handleControlKeys(KeyEvent event) {
        KeyCode keyCode = event.getCode();
        
        if (keyCode == KeyCode.BACK_SPACE) {
            if (keystrokeCount > 0) {
                // Get the length of the last keystroke
                keystrokeCount--;
                int lastKeystrokeLength = keystrokeLengths[keystrokeCount];
                
                // Zero out the bytes of the last keystroke
                for (int i = 0; i < lastKeystrokeLength; i++) {
                    passwordBytes.get()[currentLength - lastKeystrokeLength + i] = 0;
                }
                
                currentLength -= lastKeystrokeLength;
                keystrokeLengths[keystrokeCount] = 0;
            }
            updateDisplay();
            event.consume();
        } else if (keyCode == KeyCode.ESCAPE) {
            // Clear all input
            clearInput();
            updateDisplay();
            event.consume();
            if(onEscapeRunnable != null){
                onEscapeRunnable.run();
            }
        } else if (keyCode == KeyCode.U && event.isControlDown()) {
            // Ctrl+U: Unix-style clear line
            clearInput();
            updateDisplay();
            event.consume();
        } else if (keyCode == KeyCode.ENTER || keyCode == KeyCode.TAB) {
            // Allow navigation keys to pass through
            // Don't consume these - let them handle focus traversal
        } else if (isModifierKey(keyCode) || isNavigationKey(keyCode) || isSystemKey(keyCode)) {
            // Consume but don't record these keys
            event.consume();
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
        if (keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            Alerts.showAndWaitErrorAlert("Input Limit Reached", "Password cannot exceed " + MAX_KEYSTROKE_COUNT + " keystrokes.",
                getScene().getWindow(), ButtonType.OK);
         
            event.consume();
            return;
        }
        
        if (currentLength + charBytes.length > MAX_PASSWORD_LENGTH) {
            Alerts.showAndWaitErrorAlert("Input Limit Reached", "Password size " + MAX_PASSWORD_LENGTH + " reached.",
                getScene().getWindow(), ButtonType.OK);
            event.consume();
            return;
        }
        
        // Store the character bytes
        System.arraycopy(charBytes, 0, passwordBytes.get(), currentLength, charBytes.length);
        keystrokeLengths[keystrokeCount] = (byte) charBytes.length;
        currentLength += charBytes.length;
        keystrokeCount++;
        
        updateDisplay();
        event.consume();
    }
    
    private void clearInput() {
        // Zero out all password bytes
        passwordBytes.close();
        // Clear keystroke lengths
        for (int i = 0; i < keystrokeCount; i++) {
            keystrokeLengths[i] = 0;
        }
        currentLength = 0;
        keystrokeCount = 0;
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
        if (isFocused()) {
            setText(String.valueOf(FOCUSED_CHAR));
        } else {
            setText(String.valueOf(UNFOCUSED_CHAR));
        }
    }
    
    public NoteBytesEphemeral getEphemeralPassword() {
        return passwordBytes.copyOf(currentLength);
    }

    public void setOnClose(Runnable onClose){
        onCloseRunnable = onClose;
    }

    public void onEscapeRunnable(Runnable onEscape){
        onEscapeRunnable = onEscape;
    }
    
    // Method to clear the password
    public void clear() {
        clearInput();
        updateDisplay();
    }
    
    // Destroys the password (close calls clear)
    @Override
    public void close() {
        clear();
        if(onCloseRunnable != null){
            onCloseRunnable.run();
        }
    }
}