package dk.easv.eventTicketSystem.gui.events;

import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.util.EventValidationRules;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;

public class TicketTypeDialogController {

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtPrice;
    @FXML
    private TextField txtSeats;
    @FXML
    private Label errName;
    @FXML
    private Label errPrice;
    @FXML
    private Label errSeats;

    private TicketCategory ticketType;
    private boolean saved;
    private boolean validationFeedbackEnabled;

    @FXML
    public void initialize() {
        setupLiveValidation();
        clearAllErrors();
    }

    public void setTicketType(TicketCategory ticketType) {
        this.ticketType = ticketType;
        validationFeedbackEnabled = false;
        if (ticketType == null) {
            txtName.clear();
            txtPrice.clear();
            txtSeats.clear();
            clearAllErrors();
            return;
        }

        txtName.setText(ticketType.getName());
        txtPrice.setText(ticketType.getPrice() == null ? "" : ticketType.getPrice().toPlainString());
        txtSeats.setText(ticketType.getSeatCount() == null ? "" : String.valueOf(ticketType.getSeatCount()));
        clearAllErrors();
    }

    public boolean isSaved() {
        return saved;
    }

    public TicketCategory getTicketType() {
        return ticketType;
    }

    @FXML
    private void onCancel() {
        saved = false;
        close();
    }

    @FXML
    private void onSave() {
        validationFeedbackEnabled = true;
        if (!validateAll()) {
            return;
        }

        if (ticketType == null) {
            ticketType = new TicketCategory();
        }

        ticketType.setName(txtName.getText().trim());
        ticketType.setPrice(new BigDecimal(txtPrice.getText().trim()));
        ticketType.setSeatCount(Integer.parseInt(txtSeats.getText().trim()));

        saved = true;
        close();
    }

    private void setupLiveValidation() {
        txtName.textProperty().addListener((obs, oldValue, newValue) -> validateName());
        txtPrice.textProperty().addListener((obs, oldValue, newValue) -> validatePrice());
        txtSeats.textProperty().addListener((obs, oldValue, newValue) -> validateSeats());
    }

    private boolean validateAll() {
        boolean ok = true;
        ok = validateName() && ok;
        ok = validatePrice() && ok;
        ok = validateSeats() && ok;
        return ok;
    }

    private boolean validateName() {
        String value = EventValidationRules.normalizeRequired(txtName.getText());
        if (value.isEmpty()) {
            showError(errName, "Name is required.");
            return false;
        }

        if (value.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            showError(errName, "Name too long (max " + EventValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }

        clearError(errName);
        return true;
    }

    private boolean validatePrice() {
        String value = txtPrice.getText();
        if (value == null || value.isBlank()) {
            showError(errPrice, "Price is required.");
            return false;
        }

        try {
            BigDecimal price = new BigDecimal(value.trim());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                showError(errPrice, "Price cannot be negative.");
                return false;
            }
        } catch (NumberFormatException ex) {
            showError(errPrice, "Price must be a valid number.");
            return false;
        }

        clearError(errPrice);
        return true;
    }

    private boolean validateSeats() {
        String value = txtSeats.getText();
        if (value == null || value.isBlank()) {
            showError(errSeats, "Seats is required.");
            return false;
        }

        try {
            int seats = Integer.parseInt(value.trim());
            if (seats < EventValidationRules.MIN_SEAT_COUNT) {
                showError(errSeats, "Seats must be at least " + EventValidationRules.MIN_SEAT_COUNT + ".");
                return false;
            }
        } catch (NumberFormatException ex) {
            showError(errSeats, "Seats must be a whole number.");
            return false;
        }

        clearError(errSeats);
        return true;
    }

    private void showError(Label errorLabel, String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message);
        errorLabel.setVisible(validationFeedbackEnabled);
        errorLabel.setManaged(validationFeedbackEnabled);
    }

    private void clearError(Label errorLabel) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void clearAllErrors() {
        clearError(errName);
        clearError(errPrice);
        clearError(errSeats);
    }

    private void close() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.close();
    }
}
