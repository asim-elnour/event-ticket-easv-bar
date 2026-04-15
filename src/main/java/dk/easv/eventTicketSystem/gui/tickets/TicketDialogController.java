package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TicketDialogController {

    @FXML
    private TextField txtCustomerName;
    @FXML
    private TextField txtCustomerEmail;
    @FXML
    private ComboBox<TicketCategory> cmbTicketType;
    @FXML
    private TextField txtCode;
    @FXML
    private Label lblTotalPrice;
    @FXML
    private Label lblBasePrice;
    @FXML
    private Label errCustomerName;
    @FXML
    private Label errCustomerEmail;
    @FXML
    private Label errTicketCategoryId;

    private Event event;
    private TicketDraft draft;
    private boolean saved;

    @FXML
    public void initialize() {
        txtCode.setText(Ticket.generateCode());
        txtCode.setEditable(false);
        cmbTicketType.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TicketCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        cmbTicketType.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TicketCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        setupLiveValidation();
        updateTotalPrice();
    }

    public void setEvent(Event event) {
        this.event = event;
        populateTicketTypes();
        updateTotalPrice();
    }

    public boolean isSaved() {
        return saved;
    }

    public TicketDraft getDraft() {
        return draft;
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

        TicketCategory selectedType = cmbTicketType.getSelectionModel().getSelectedItem();

        draft = new TicketDraft(
                txtCustomerName.getText().trim(),
                txtCustomerEmail.getText().trim(),
                selectedType == null ? null : selectedType.getId(),
                txtCode.getText().trim()
        );
        saved = true;
        close();
    }

    private void close() {
        Stage stage = (Stage) txtCustomerName.getScene().getWindow();
        stage.close();
    }

    private void setupLiveValidation() {
        txtCustomerName.textProperty().addListener((obs, oldValue, newValue) -> validateCustomerName());
        txtCustomerEmail.textProperty().addListener((obs, oldValue, newValue) -> validateCustomerEmail());
        cmbTicketType.valueProperty().addListener((obs, oldValue, newValue) -> {
            validateTicketCategoryId();
            updateTotalPrice();
        });
    }

    private boolean validateAll() {
        boolean ok = true;
        ok = validateCustomerName() && ok;
        ok = validateCustomerEmail() && ok;
        ok = validateTicketCategoryId() && ok;
        return ok;
    }

    private boolean validateCustomerName() {
        String value = txtCustomerName.getText();
        if (value == null || value.isBlank()) {
            errCustomerName.setText("Customer name is required.");
            errCustomerName.setVisible(true);
            return false;
        }
        if (value.length() > 255) {
            errCustomerName.setText("Customer name too long (max 255).");
            errCustomerName.setVisible(true);
            return false;
        }
        errCustomerName.setVisible(false);
        return true;
    }

    private boolean validateCustomerEmail() {
        String value = txtCustomerEmail.getText();
        if (value == null || value.isBlank()) {
            errCustomerEmail.setText("Customer email is required.");
            errCustomerEmail.setVisible(true);
            return false;
        }
        if (value.length() > 255) {
            errCustomerEmail.setText("Customer email too long (max 255).");
            errCustomerEmail.setVisible(true);
            return false;
        }
        errCustomerEmail.setVisible(false);
        return true;
    }

    private boolean validateTicketCategoryId() {
        TicketCategory selectedType = cmbTicketType.getSelectionModel().getSelectedItem();
        if (selectedType == null || selectedType.getId() == null) {
            errTicketCategoryId.setText("Ticket type is required.");
            errTicketCategoryId.setVisible(true);
            return false;
        }
        errTicketCategoryId.setVisible(false);
        return true;
    }

    private void populateTicketTypes() {
        cmbTicketType.getItems().clear();
        if (event == null) {
            return;
        }

        for (TicketCategory category : event.getTicketTypes()) {
            if (category != null && !category.isDeleted()) {
                cmbTicketType.getItems().add(category);
            }
        }
        if (!cmbTicketType.getItems().isEmpty()) {
            cmbTicketType.getSelectionModel().selectFirst();
        }
    }

    private void updateTotalPrice() {
        TicketCategory selectedType = cmbTicketType.getSelectionModel().getSelectedItem();
        BigDecimal basePrice = normalizeMoney(selectedType == null ? null : selectedType.getPrice());
        BigDecimal totalPrice = roundMoney(basePrice);

        lblBasePrice.setText(basePrice.toPlainString());
        lblTotalPrice.setText(totalPrice.toPlainString());
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return roundMoney(value);
    }

    private BigDecimal roundMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record TicketDraft(String customerName,
                              String customerEmail,
                              Long ticketCategoryId,
                              String code) {
    }
}
