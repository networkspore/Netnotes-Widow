package io.netnotes.gui.fx.components.notifications;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

public class Alerts {

    public static Optional<ButtonType> showAndWaitErrorAlert(String header, String msg, Window window, ButtonType... buttonTypes){
        Alert errorAlert = new Alert(AlertType.NONE, msg, buttonTypes);
        errorAlert.setTitle(header);
        errorAlert.setHeaderText(header);
        errorAlert.initOwner(window);
        return errorAlert.showAndWait();
    }

    public static void showErrorAlert(String header, String msg, Window window, ButtonType... buttonTypes){
        Alert errorAlert = new Alert(AlertType.NONE, msg, buttonTypes);
        errorAlert.setTitle(header);
        errorAlert.setHeaderText(header);
        errorAlert.initOwner(window);
        errorAlert.show();
    }
}
