package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.common.MessageDialogController;
import javafx.geometry.Rectangle2D;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

public final class DialogUtils {

    private DialogUtils() {}

    public static void showWarning(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getDialogPane().setGraphic(null);
        alert.showAndWait();
    }

    public static void showError(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getDialogPane().setGraphic(null);
        alert.showAndWait();
    }

    public static void showInfo(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getDialogPane().setGraphic(null);
        alert.showAndWait();
    }

    public static boolean showConfirmation(String title,
                                           String header,
                                           String message,
                                           String confirmButtonText,
                                           Window owner) {
        ButtonType confirmButton = new ButtonType(
                confirmButtonText == null || confirmButtonText.isBlank() ? "OK" : confirmButtonText,
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, confirmButton, cancelButton);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.getDialogPane().setGraphic(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        return alert.showAndWait()
                .filter(button -> button == confirmButton)
                .isPresent();
    }

    public static boolean confirmAction(ActionDialogType actionType, String targetName, Window owner) {
        if (actionType == null) {
            return false;
        }
        return showConfirmation(
                actionType.getWindowTitle(),
                actionType.getHeaderText(targetName),
                actionType.getContentText(),
                actionType.getConfirmButtonText(),
                owner
        );
    }

    public static void showDetailedError(String title, String message, String type, String error) {
        showDetailedError(title, message, type, error, null);
    }

    public static void showDetailedError(String title, String message, String type, String error, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogUtils.class.getResource(ViewType.MESSAGE_DIALOG.getFxmlPath()));
            Parent root = loader.load();

            MessageDialogController controller = loader.getController();
            controller.configureDetailed(
                    title,
                    message,
                    type,
                    error,
                    "OK"
            );

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(title == null || title.isBlank() ? "Information" : title);
            stage.setScene(new Scene(root));
            configureHalfScreenDialogStage(stage);
            stage.showAndWait();
            return;
        } catch (IOException ignored) {
            // Fall back to JavaFX alert if custom dialog fails to load.
        }

        StringBuilder fallbackMessage = new StringBuilder(message == null ? "" : message);
        if (type != null && !type.isBlank()) {
            fallbackMessage.append("\n").append("Type").append(": ").append(type);
        }
        if (error != null && !error.isBlank()) {
            fallbackMessage.append("\n").append("Error").append(": ").append(error);
        }
        showError(title, null, fallbackMessage.toString());
    }

    public static void showMessageDialog(String title, String message, String buttonText, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogUtils.class.getResource(ViewType.MESSAGE_DIALOG.getFxmlPath()));
            Parent root = loader.load();

            MessageDialogController controller = loader.getController();
            controller.configure(title, message, buttonText);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(title == null || title.isBlank() ? "Information" : title);
            stage.setScene(new Scene(root));
            configureHalfScreenDialogStage(stage);
            stage.showAndWait();
            return;
        } catch (IOException ignored) {
            // Fall back to JavaFX alert if custom dialog fails to load.
        }

        showInfo(title, null, message);
    }

    public static void configureDialogStage(Stage stage) {
        if (stage == null) {
            return;
        }

        Rectangle2D bounds = resolveVisualBounds(stage);

        stage.setResizable(true);
        stage.setMinWidth(420);
        stage.setMinHeight(320);
        stage.setMaxWidth(bounds.getWidth());
        stage.setMaxHeight(bounds.getHeight());
        stage.setWidth(Math.min(Math.max(stage.getWidth(), 520), bounds.getWidth() * 0.9));
        stage.setHeight(Math.min(Math.max(stage.getHeight(), 480), bounds.getHeight() * 0.9));
    }

    public static void configureHalfScreenDialogStage(Stage stage) {
        if (stage == null) {
            return;
        }

        Rectangle2D bounds = resolveVisualBounds(stage);
        double maxWidth = bounds.getWidth();
        double maxHeight = bounds.getHeight();
        double minWidth = Math.min(520, maxWidth);
        double minHeight = Math.min(380, maxHeight);
        double targetWidth = clamp(bounds.getWidth() * 0.5, minWidth, maxWidth);
        double targetHeight = clamp(bounds.getHeight() * 0.5, minHeight, maxHeight);

        stage.setResizable(true);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        stage.setMaxWidth(maxWidth);
        stage.setMaxHeight(maxHeight);
        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);
        stage.setX(bounds.getMinX() + (bounds.getWidth() - targetWidth) / 2.0);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - targetHeight) / 2.0);
    }

    private static Rectangle2D resolveVisualBounds(Stage stage) {
        Window owner = stage.getOwner();
        if (owner != null) {
            var screens = Screen.getScreensForRectangle(
                    owner.getX(),
                    owner.getY(),
                    Math.max(owner.getWidth(), 1),
                    Math.max(owner.getHeight(), 1)
            );
            if (!screens.isEmpty()) {
                return screens.get(0).getVisualBounds();
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
