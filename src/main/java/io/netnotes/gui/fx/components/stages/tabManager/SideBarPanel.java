package io.netnotes.gui.fx.components.stages.tabManager;

import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import java.util.ArrayList;
import java.util.List;

import io.netnotes.gui.fx.app.FxResourceFactory;
import io.netnotes.gui.fx.components.buttons.BufferedButton;
import io.netnotes.gui.fx.components.menus.BufferedMenuButton;

public class SideBarPanel extends VBox {
    private final VBox buttonContainer;
    private final BufferedMenuButton settingsButton;
    private final Button expandButton;
    private final List<BufferedButton> buttons;
    private boolean isExpanded = true;
    
    private final DoubleProperty contentWidth;
    private final DoubleProperty contentHeight;
    
    public SideBarPanel(DoubleProperty contentWidth, DoubleProperty contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.buttons = new ArrayList<>();
        
        this.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3c3c3c; -fx-border-width: 0 1 0 0;");
        this.setPrefWidth(200);
        this.setMinWidth(200);
        this.setMaxWidth(200);
        
        // Expand/collapse button
        expandButton = new Button("≡");
        expandButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                             "-fx-font-size: 16px; -fx-padding: 8px;");
        expandButton.setMaxWidth(Double.MAX_VALUE);
        expandButton.setOnAction(e -> toggleExpanded());
        
        // Button container
        buttonContainer = new VBox(5);
        buttonContainer.setPadding(new Insets(10));
        VBox.setVgrow(buttonContainer, Priority.ALWAYS);
        
        // Settings button
        settingsButton = new BufferedMenuButton(FxResourceFactory.SETTINGS_ICON);
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        this.getChildren().addAll(expandButton, buttonContainer, spacer, settingsButton);
        this.setPadding(new Insets(0, 0, 10, 0));
    }
    
    public void addButton(BufferedButton button) {
        button.setMaxWidth(Double.MAX_VALUE);
        buttons.add(button);
        buttonContainer.getChildren().add(button);
    }
    
    public void removeButton(BufferedButton button) {
        buttons.remove(button);
        buttonContainer.getChildren().remove(button);
    }
    
    public void clearButtons() {
        buttons.clear();
        buttonContainer.getChildren().clear();
    }
    
    public BufferedMenuButton getSettingsButton() {
        return settingsButton;
    }
    
    public Button getExpandButton() {
        return expandButton;
    }
    
    private void toggleExpanded() {
        isExpanded = !isExpanded;
        if (isExpanded) {
            this.setPrefWidth(200);
            this.setMinWidth(200);
            this.setMaxWidth(200);
            expandButton.setText("≡");
        } else {
            this.setPrefWidth(50);
            this.setMinWidth(50);
            this.setMaxWidth(50);
            expandButton.setText("→");
        }
    }
    
    public boolean isExpanded() {
        return isExpanded;
    }
}