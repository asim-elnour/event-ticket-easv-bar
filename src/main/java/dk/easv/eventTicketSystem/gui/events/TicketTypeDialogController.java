package dk.easv.eventTicketSystem.gui.events;

import dk.easv.eventTicketSystem.be.TicketCategory;
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

    @FXML
    public void initialize() {
        setupLiveValidation();
    }

    public void setTicketType(TicketCategory ticketType) {
        this.ticketType = ticketType;
        if (ticketType == null) {
            txtName.clear();
            txtPrice.clear();
            txtSeats.clear();
            return;
        }

        txtName.setText(ticketType.getName());
        txtPrice.setText(ticketType.getPrice() == null ? "" : ticketType.getPrice().toPlainString());
        txtSeats.setText(ticketType.getSeatCount() == null ? "" : String.valueOf(ticketType.getSeatCount()));
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
        String value = txtName.getText();
        if (value == null || value.isBlank()) {
            errName.setText("Name is required.");
            errName.setVisible(true);
            return false;
        }

        if (value.length() > 255) {
            errName.setText("Name too long (max 255).");
            errName.setVisible(true);
            return false;
        }

        errName.setVisible(false);
        return true;
    }

    private boolean validatePrice() {
        String value = txtPrice.getText();
        if (value == null || value.isBlank()) {
            errPrice.setText("Price is required.");
            errPrice.setVisible(true);
            return false;
        }

        try {
            BigDecimal price = new BigDecimal(value.trim());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                errPrice.setText("Price cannot be negative.");
                errPrice.setVisible(true);
                return false;
            }
        } catch (NumberFormatException ex) {
            errPrice.setText("Price must be a valid number.");
            errPrice.setVisible(true);
            return false;
        }

        errPrice.setVisible(false);
        return true;
    }

    private boolean validateSeats() {
        String value = txtSeats.getText();
        if (value == null || value.isBlank()) {
            errSeats.setText("Seats / number is required.");
            errSeats.setVisible(true);
            return false;
        }

        try {
            int seats = Integer.parseInt(value.trim());
            if (seats <= 0) {
                errSeats.setText("Seats / number must be greater than 0.");
                errSeats.setVisible(true);
                return false;
            }
        } catch (NumberFormatException ex) {
            errSeats.setText("Seats / number must be a whole number.");
            errSeats.setVisible(true);
            return false;
        }

        errSeats.setVisible(false);
        return true;
    }

    private void close() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.close();
    }
}
