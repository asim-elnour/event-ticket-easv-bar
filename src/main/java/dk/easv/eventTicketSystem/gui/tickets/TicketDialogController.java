package dk.easv.eventTicketSystem.gui.tickets;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.bll.CustomerLogic;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.CustomerValidationRules;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TicketDialogController {

    @FXML
    private BorderPane dialogPane;
    @FXML
    private ScrollPane dialogScrollPane;
    @FXML
    private VBox loadingOverlay;
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnSave;
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

    private AppModel model;
    private Event event;
    private TicketDraft draft;
    private boolean saved;
    private boolean saving;
    private boolean customerDirectoryLoading;
    private boolean customerDirectoryLoaded;
    private String customerDirectoryErrorMessage = "";
    private final Label existingCustomersPlaceholder = new Label("Type to search customers.");

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

        colExistingCustomerName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colExistingCustomerEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());
        existingCustomersTable.setItems(filteredCustomers);
        existingCustomersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        existingCustomersPlaceholder.getStyleClass().add("muted-text");
        existingCustomersTable.setPlaceholder(existingCustomersPlaceholder);
        existingCustomersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                txtCustomerName.setText(newValue.getName());
                txtCustomerEmail.setText(newValue.getEmail());
                errExistingCustomer.setVisible(false);
            }
        });

        txtExistingCustomerSearch.textProperty().addListener((obs, oldValue, newValue) -> handleExistingCustomerSearchChanged());

        setupLiveValidation();
        filteredCustomers.setPredicate(customer -> false);
        updateExistingCustomersPlaceholder();
        updatePrice();
        updateSavingState(false);
        installCloseGuard();
    }

    public void setModel(AppModel model) {
        this.model = model;
    }

    public void setEvent(Event event) {
        this.event = event;
        populateTicketTypes();
        resetCustomerDirectoryState();
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
        if (saving) {
            return;
        }
        saved = false;
        close();
    }

    @FXML
    private void onSave() {
        if (saving) {
            return;
        }
        if (!validateAll()) {
            return;
        }
        if (model == null || event == null || event.getId() == null || event.getId() <= 0) {
            DialogUtils.showError("Add Ticket", null, "Ticket dialog is missing the selected event.");
            return;
        }

        TicketCategory selectedType = cmbTicketType.getSelectionModel().getSelectedItem();
        draft = new TicketDraft(
                txtCustomerName.getText().trim(),
                txtCustomerEmail.getText().trim(),
                selectedType == null ? null : selectedType.getId(),
                txtCode.getText().trim()
        );
        persistDraft();
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
            updatePrice();
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
        customerDirectoryLoaded = false;
        errExistingCustomer.setVisible(false);
        updateExistingCustomersPlaceholder();

        Task<java.util.List<Customer>> task = new Task<>() {
            @Override
            protected java.util.List<Customer> call() throws Exception {
                return customerLogic.getCustomerDirectory();
            }
        };

        task.setOnSucceeded(event -> {
            customerDirectoryLoading = false;
            customerDirectoryLoaded = true;
            customerDirectoryErrorMessage = "";
            customerDirectory.setAll(task.getValue());
            applyCustomerDirectoryFilter();
        });

        task.setOnFailed(event -> {
            customerDirectoryLoading = false;
            customerDirectoryLoaded = false;
            customerDirectoryErrorMessage = task.getException() instanceof CustomerException
                    ? task.getException().getMessage()
                    : "Unable to load existing customers.";
            customerDirectory.clear();
            filteredCustomers.setPredicate(customer -> false);
            errExistingCustomer.setText(customerDirectoryErrorMessage);
            errExistingCustomer.setVisible(true);
            updateExistingCustomersPlaceholder();
        });

        new Thread(task, "load-customer-directory-task").start();
    }

    private void handleExistingCustomerSearchChanged() {
        String query = normalizeSearchQuery();
        if (query.isEmpty()) {
            filteredCustomers.setPredicate(customer -> false);
            existingCustomersTable.getSelectionModel().clearSelection();
            errExistingCustomer.setVisible(false);
            updateExistingCustomersPlaceholder();
            return;
        }

        if (!customerDirectoryLoaded) {
            if (!customerDirectoryLoading) {
                loadCustomerDirectory();
            } else {
                updateExistingCustomersPlaceholder();
            }
            return;
        }

        applyCustomerDirectoryFilter();
    }

    private void applyCustomerDirectoryFilter() {
        String query = normalizeSearchQuery();
        if (query.isEmpty()) {
            filteredCustomers.setPredicate(customer -> false);
            updateExistingCustomersPlaceholder();
            return;
        }
        filteredCustomers.setPredicate(customer ->
                customer != null
                        && CustomerValidationRules.matchesSearch(query, customer.getName(), customer.getEmail()));
        updateExistingCustomersPlaceholder();
    }

    private void resetCustomerDirectoryState() {
        customerDirectoryLoading = false;
        customerDirectoryLoaded = false;
        customerDirectoryErrorMessage = "";
        customerDirectory.clear();
        filteredCustomers.setPredicate(customer -> false);
        existingCustomersTable.getSelectionModel().clearSelection();
        errExistingCustomer.setVisible(false);
        updateExistingCustomersPlaceholder();
    }

    private void updateExistingCustomersPlaceholder() {
        if (customerDirectoryLoading) {
            existingCustomersPlaceholder.setText("Loading customers...");
            return;
        }
        if (!customerDirectoryErrorMessage.isBlank()) {
            existingCustomersPlaceholder.setText("Could not load customers.");
            return;
        }
        if (normalizeSearchQuery().isEmpty()) {
            existingCustomersPlaceholder.setText("Type to search customers.");
            return;
        }
        existingCustomersPlaceholder.setText("No customers found.");
    }

    private String normalizeSearchQuery() {
        String query = txtExistingCustomerSearch == null ? "" : txtExistingCustomerSearch.getText();
        return query == null ? "" : query.trim();
    }

    private void persistDraft() {
        updateSavingState(true);

        Task<Ticket> task = new Task<>() {
            @Override
            protected Ticket call() throws Exception {
                return model.addTicket(
                        event,
                        draft.ticketCategoryId(),
                        draft.customerName(),
                        draft.customerEmail(),
                        draft.code()
                );
            }
        };

        task.setOnSucceeded(workerStateEvent -> {
            saved = true;
            updateSavingState(false);
            close();
        });

        task.setOnFailed(workerStateEvent -> {
            updateSavingState(false);
            Throwable throwable = task.getException();
            DialogUtils.showError(
                    "Add Ticket",
                    null,
                    throwable == null ? "Unable to add ticket." : throwable.getMessage()
            );
        });

        Thread thread = new Thread(task, "add-ticket-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSavingState(boolean saving) {
        this.saving = saving;
        if (dialogPane != null) {
            dialogPane.setDisable(saving);
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(saving);
            loadingOverlay.setManaged(saving);
        }
        if (btnSave != null) {
            btnSave.setDisable(saving);
        }
        if (btnCancel != null) {
            btnCancel.setDisable(saving);
        }
        if (txtCustomerName != null && txtCustomerName.getScene() != null) {
            txtCustomerName.getScene().setCursor(saving ? Cursor.WAIT : Cursor.DEFAULT);
        } else if (dialogScrollPane != null) {
            dialogScrollPane.setCursor(saving ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }

    private void installCloseGuard() {
        txtCustomerName.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }

            if (newScene.getWindow() instanceof Stage stage) {
                stage.setOnCloseRequest(event -> {
                    if (saving) {
                        event.consume();
                    }
                });
            }

            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    stage.setOnCloseRequest(event -> {
                        if (saving) {
                            event.consume();
                        }
                    });
                }
            });
        });
    }

    public record TicketDraft(String customerName,
                              String customerEmail,
                              Long ticketCategoryId,
                              String code) {
    }
}
