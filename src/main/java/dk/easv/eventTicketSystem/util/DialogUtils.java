package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.common.ActionConfirmDialogController;
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
        if (showMessageDialogInternal(title, header, message, "OK", null)) {
            return;
        }
        showFallbackAlert(Alert.AlertType.WARNING, title, header, message);
    }

    public static void showError(String title, String header, String message) {
        if (showMessageDialogInternal(title, header, message, "OK", null)) {
            return;
        }
        showFallbackAlert(Alert.AlertType.ERROR, title, header, message);
    }

    public static void showInfo(String title, String header, String message) {
        if (showMessageDialogInternal(title, header, message, "OK", null)) {
            return;
        }
        showFallbackAlert(Alert.AlertType.INFORMATION, title, header, message);
    }

    public static boolean showConfirmation(String title,
                                           String header,
                                           String message,
                                           String confirmButtonText,
                                           Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogUtils.class.getResource(ViewType.ACTION_CONFIRM_DIALOG.getFxmlPath()));
            Parent root = loader.load();

            ActionConfirmDialogController controller = loader.getController();
            controller.configure(
                    resolveHeaderText(title, header),
                    safeMessage(message),
                    confirmButtonText
            );

            Stage stage = createModalDialogStage(title, root, owner);
            configureHalfScreenDialogStage(stage);
            stage.showAndWait();
            return controller.isConfirmed();
        } catch (IOException ignored) {
            // Fall back to JavaFX alert if custom dialog fails to load.
        }

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
                    resolveHeaderText(title, null),
                    safeMessage(message),
                    null,
                    error,
                    "OK"
            );

            Stage stage = createModalDialogStage(title, root, owner);
            configureHalfScreenDialogStage(stage);
            stage.showAndWait();
            return;
        } catch (IOException ignored) {
            // Fall back to JavaFX alert if custom dialog fails to load.
        }

        StringBuilder fallbackMessage = new StringBuilder(message == null ? "" : message);
        if (error != null && !error.isBlank()) {
            fallbackMessage.append("\n\n").append(error);
        }
        showFallbackAlert(Alert.AlertType.ERROR, title, null, fallbackMessage.toString());
    }

    public static void showMessageDialog(String title, String message, String buttonText, Window owner) {
        if (showMessageDialogInternal(title, title, message, buttonText, owner)) {
            return;
        }

        showFallbackAlert(Alert.AlertType.INFORMATION, title, null, message);
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

    private static boolean showMessageDialogInternal(String title,
                                                     String header,
                                                     String message,
                                                     String buttonText,
                                                     Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogUtils.class.getResource(ViewType.MESSAGE_DIALOG.getFxmlPath()));
            Parent root = loader.load();

            MessageDialogController controller = loader.getController();
            controller.configure(
                    resolveHeaderText(title, header),
                    safeMessage(message),
                    buttonText
            );

            Stage stage = createModalDialogStage(title, root, owner);
            configureHalfScreenDialogStage(stage);
            stage.showAndWait();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Stage createModalDialogStage(String title, Parent root, Window owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title == null || title.isBlank() ? "Information" : title);
        stage.setScene(new Scene(root));
        return stage;
    }

    private static String resolveHeaderText(String title, String header) {
        if (header != null && !header.isBlank()) {
            return header;
        }
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "Information";
    }

    private static String safeMessage(String message) {
        return message == null ? "" : message;
    }

    private static void showFallbackAlert(Alert.AlertType type, String title, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getDialogPane().setGraphic(null);
        alert.showAndWait();
    }
}
