package io.netnotes.gui.fx.app.apps;


import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class AppManager extends AppBox {
    private TableView<Plugin> table;
    private Label statusLabel;
    
    public PluginManager(DoubleProperty contentWidth, DoubleProperty contentHeight) {
        super(contentWidth, contentHeight);
    }
    
    @Override
    protected void initialize() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(20));
        
        // Header with actions
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label header = new Label("Plugin Management");
        header.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        
        Button addBtn = new Button("+ Add Plugin");
        addBtn.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                       "-fx-padding: 8px 15px; -fx-background-radius: 5px;");
        
        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #ffffff; " +
                           "-fx-padding: 8px 15px; -fx-background-radius: 5px;");
        
        headerBox.getChildren().addAll(header, addBtn, refreshBtn);
        
        // Table
        table = new TableView<>();
        table.setStyle("-fx-background-color: #2b2b2b;");
        
        TableColumn<Plugin, String> nameCol = new TableColumn<>("Plugin Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.prefWidthProperty().bind(contentWidth.multiply(0.25));
        
        TableColumn<Plugin, String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(data -> data.getValue().versionProperty());
        versionCol.prefWidthProperty().bind(contentWidth.multiply(0.12));
        
        TableColumn<Plugin, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.prefWidthProperty().bind(contentWidth.multiply(0.15));
        
        TableColumn<Plugin, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(data -> data.getValue().descriptionProperty());
        descCol.prefWidthProperty().bind(contentWidth.multiply(0.48));
        
        table.getColumns().addAll(nameCol, versionCol, statusCol, descCol);
        
        // Sample data
        ObservableList<Plugin> plugins = FXCollections.observableArrayList(
            new Plugin("Database Connector", "1.2.3", "Active", "Connects to external databases"),
            new Plugin("API Integration", "2.0.1", "Active", "REST API integration module"),
            new Plugin("Analytics", "1.5.0", "Inactive", "Advanced analytics and reporting"),
            new Plugin("Email Service", "3.1.0", "Active", "Email notifications and templates"),
            new Plugin("File Storage", "2.2.0", "Active", "Cloud file storage integration")
        );
        table.setItems(plugins);
        
        // Status label
        statusLabel = new Label(plugins.size() + " plugins loaded");
        statusLabel.setStyle("-fx-text-fill: #888888;");
        
        container.getChildren().addAll(headerBox, table, statusLabel);
        this.setCenter(container);
    }
    
    @Override
    protected void onHeightChanged(double newHeight) {
        if (table != null) {
            table.setPrefHeight(newHeight - 120);
        }
    }

}
