package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.bll.CustomerLogic;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.util.CustomerValidationRules;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TicketDialogController {

    @FXML
    private ChoiceBox<CustomerMode> customerModeChoice;
    @FXML
    private TextField txtCustomerName;
    @FXML
    private TextField txtCustomerEmail;
    @FXML
    private ComboBox<TicketCategory> cmbTicketType;
    @FXML
    private TextField txtCode;
    @FXML
    private Label lblPrice;
    @FXML
    private TextField txtExistingCustomerSearch;
    @FXML
    private TableView<Customer> existingCustomersTable;
    @FXML
    private TableColumn<Customer, String> colExistingCustomerName;
    @FXML
    private TableColumn<Customer, String> colExistingCustomerEmail;
    @FXML
    private javafx.scene.layout.VBox existingCustomerSection;
    @FXML
    private Label errCustomerName;
    @FXML
    private Label errCustomerEmail;
    @FXML
    private Label errTicketCategoryId;
    @FXML
    private Label errExistingCustomer;

    private final CustomerLogic customerLogic = new CustomerLogic();
    private final ObservableList<Customer> customerDirectory = FXCollections.observableArrayList();
    private final FilteredList<Customer> filteredCustomers = new FilteredList<>(customerDirectory, customer -> true);

    private Event event;
    private TicketDraft draft;
    private boolean saved;
    private boolean customerDirectoryLoading;
    private String customerDirectoryErrorMessage = "";

    @FXML
    public void initialize() {
        txtCode.setText(Ticket.generateCode());
        txtCode.setEditable(false);

        customerModeChoice.getItems().setAll(CustomerMode.values());
        customerModeChoice.getSelectionModel().select(CustomerMode.NEW_CUSTOMER);
        customerModeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateCustomerModeState();
            validateCurrentCustomerState();
        });

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

        colExistingCustomerName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colExistingCustomerEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());
        existingCustomersTable.setItems(filteredCustomers);
        existingCustomersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        existingCustomersTable.setPlaceholder(new Label("No customers found."));
        existingCustomersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                txtCustomerName.setText(newValue.getName());
                txtCustomerEmail.setText(newValue.getEmail());
                errExistingCustomer.setVisible(false);
            }
            validateCurrentCustomerState();
        });

        txtExistingCustomerSearch.textProperty().addListener((obs, oldValue, newValue) -> applyCustomerDirectoryFilter());

        setupLiveValidation();
        updateCustomerModeState();
        updatePrice();
    }

    public void setEvent(Event event) {
        this.event = event;
        populateTicketTypes();
        loadCustomerDirectory();
        updatePrice();
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
        txtCustomerName.textProperty().addListener((obs, oldValue, newValue) -> {
            if (isNewCustomerMode()) {
                validateCustomerName();
            }
        });
        txtCustomerEmail.textProperty().addListener((obs, oldValue, newValue) -> {
            if (isNewCustomerMode()) {
                validateCustomerEmail();
            }
        });
        cmbTicketType.valueProperty().addListener((obs, oldValue, newValue) -> {
            validateTicketCategoryId();
            updatePrice();
        });
    }

    private boolean validateAll() {
        boolean ok = true;
        ok = validateCurrentCustomerState() && ok;
        ok = validateTicketCategoryId() && ok;
        return ok;
    }

    private boolean validateCurrentCustomerState() {
        if (isNewCustomerMode()) {
            errExistingCustomer.setVisible(false);
            return validateCustomerName() & validateCustomerEmail();
        }
        boolean validSelection = validateExistingCustomerSelection();
        if (validSelection) {
            errCustomerName.setVisible(false);
            errCustomerEmail.setVisible(false);
        }
        return validSelection;
    }

    private boolean validateCustomerName() {
        String value = txtCustomerName.getText();
        if (value == null || value.isBlank()) {
            errCustomerName.setText("Customer name is required.");
            errCustomerName.setVisible(true);
            return false;
        }
        if (value.trim().length() > CustomerValidationRules.MAX_TEXT_LENGTH) {
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
        if (!CustomerValidationRules.isValidEmail(value)) {
            errCustomerEmail.setText("Customer email must be valid.");
            errCustomerEmail.setVisible(true);
            return false;
        }
        errCustomerEmail.setVisible(false);
        return true;
    }

    private boolean validateExistingCustomerSelection() {
        if (!customerDirectoryErrorMessage.isBlank()) {
            errExistingCustomer.setText(customerDirectoryErrorMessage);
            errExistingCustomer.setVisible(true);
            return false;
        }
        if (customerDirectoryLoading) {
            errExistingCustomer.setText("Customers are still loading.");
            errExistingCustomer.setVisible(true);
            return false;
        }
        Customer selectedCustomer = existingCustomersTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) {
            errExistingCustomer.setText("Please select an existing customer.");
            errExistingCustomer.setVisible(true);
            return false;
        }
        txtCustomerName.setText(selectedCustomer.getName());
        txtCustomerEmail.setText(selectedCustomer.getEmail());
        errExistingCustomer.setVisible(false);
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

    private void updatePrice() {
        TicketCategory selectedType = cmbTicketType.getSelectionModel().getSelectedItem();
        BigDecimal basePrice = normalizeMoney(selectedType == null ? null : selectedType.getPrice());
        lblPrice.setText(basePrice.toPlainString());
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void loadCustomerDirectory() {
        customerDirectoryLoading = true;
        customerDirectoryErrorMessage = "";
        errExistingCustomer.setVisible(false);

        Task<java.util.List<Customer>> task = new Task<>() {
            @Override
            protected java.util.List<Customer> call() throws Exception {
                return customerLogic.getCustomerDirectory();
            }
        };

        task.setOnSucceeded(event -> {
            customerDirectoryLoading = false;
            customerDirectoryErrorMessage = "";
            customerDirectory.setAll(task.getValue());
            applyCustomerDirectoryFilter();
        });

        task.setOnFailed(event -> {
            customerDirectoryLoading = false;
            customerDirectoryErrorMessage = task.getException() instanceof CustomerException
                    ? task.getException().getMessage()
                    : "Unable to load existing customers.";
            errExistingCustomer.setText(customerDirectoryErrorMessage);
            errExistingCustomer.setVisible(isExistingCustomerMode());
        });

        new Thread(task, "load-customer-directory-task").start();
    }

    private void applyCustomerDirectoryFilter() {
        String query = txtExistingCustomerSearch.getText();
        filteredCustomers.setPredicate(customer ->
                customer != null
                        && CustomerValidationRules.matchesSearch(query, customer.getName(), customer.getEmail()));
    }

    private void updateCustomerModeState() {
        boolean existingMode = isExistingCustomerMode();
        existingCustomerSection.setVisible(existingMode);
        existingCustomerSection.setManaged(existingMode);
        txtExistingCustomerSearch.setDisable(!existingMode);
        existingCustomersTable.setDisable(!existingMode);
        txtCustomerName.setEditable(!existingMode);
        txtCustomerEmail.setEditable(!existingMode);
        if (existingMode) {
            txtCustomerName.clear();
            txtCustomerEmail.clear();
            existingCustomersTable.getSelectionModel().clearSelection();
            errExistingCustomer.setVisible(!customerDirectoryErrorMessage.isBlank());
            if (!customerDirectoryErrorMessage.isBlank()) {
                errExistingCustomer.setText(customerDirectoryErrorMessage);
            }
        } else {
            errExistingCustomer.setVisible(false);
        }
    }

    private boolean isNewCustomerMode() {
        return customerModeChoice.getValue() != CustomerMode.EXISTING_CUSTOMER;
    }

    private boolean isExistingCustomerMode() {
        return customerModeChoice.getValue() == CustomerMode.EXISTING_CUSTOMER;
    }

    public enum CustomerMode {
        NEW_CUSTOMER("New Customer"),
        EXISTING_CUSTOMER("Existing Customer");

        private final String label;

        CustomerMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record TicketDraft(String customerName,
                              String customerEmail,
                              Long ticketCategoryId,
                              String code) {
    }
}
