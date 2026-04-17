package dk.easv.eventTicketSystem.gui.common;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ActionConfirmDialogController {

    @FXML private Label headerLabel;
    @FXML private Label contentLabel;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private boolean confirmed;

    public void configure(String header, String message, String confirmText) {
        headerLabel.setText(header == null || header.isBlank() ? "Confirm action" : header);
        contentLabel.setText(message == null ? "" : message);
        confirmButton.setText(confirmText == null || confirmText.isBlank() ? "Confirm" : confirmText);
        cancelButton.setText("Cancel");
        confirmed = false;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    private void onConfirm() {
        confirmed = true;
        close();
    }

    @FXML
    private void onCancel() {
        confirmed = false;
        close();
    }

    private void close() {
        Stage stage = (Stage) confirmButton.getScene().getWindow();
        stage.close();
    }
}
