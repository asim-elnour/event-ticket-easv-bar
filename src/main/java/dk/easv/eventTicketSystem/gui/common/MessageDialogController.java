package dk.easv.eventTicketSystem.gui.common;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MessageDialogController {

    @FXML private Label headerLabel;
    @FXML private Label contentLabel;
    @FXML private VBox detailsBox;
    @FXML private Label detailLabel;
    @FXML private Button okButton;

    @FXML
    public void initialize() {
        setDetailsVisible(false);
    }

    public void configure(String header, String message, String buttonText) {
        configureDetailed(header, message, null, null, buttonText);
    }

    public void configureDetailed(String header, String message, String type, String error, String buttonText) {
        headerLabel.setText(header == null || header.isBlank()
                ? "Information"
                : header);
        contentLabel.setText(message == null ? "" : message);
        okButton.setText(buttonText == null || buttonText.isBlank()
                ? "OK"
                : buttonText);

        boolean hasDetail = error != null && !error.isBlank();
        if (hasDetail) {
            detailLabel.setText(error);
            setDetailsVisible(true);
            return;
        }

        setDetailsVisible(false);
    }

    private void setDetailsVisible(boolean visible) {
        detailsBox.setManaged(visible);
        detailsBox.setVisible(visible);
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}
