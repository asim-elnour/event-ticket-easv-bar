package dk.easv.eventTicketSystem.gui.customers;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.DataViewMode;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.StatusBanner;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class CustomersController implements ModelAware {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Customer> customersTable;
    @FXML
    private TableColumn<Customer, String> colCustomerTableName;
    @FXML
    private TableColumn<Customer, String> colCustomerTableEmail;
    @FXML
    private Button showDeletedButton;
    @FXML
    private ChoiceBox<DataViewMode> viewChoice;

    private final Label placeholderLabel = new Label("No customers found.");
    private final ListChangeListener<Customer> customersListener = change -> {
        updateShowDeletedButtonText();
        updatePlaceholder();
        restoreSelection();
    };

    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        placeholderLabel.getStyleClass().add("muted-text");

        colCustomerTableName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colCustomerTableEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());

        customersTable.setPlaceholder(placeholderLabel);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCustomer, newCustomer) -> {
            if (model != null) {
                model.setSelectedCustomer(newCustomer);
            }
        });

        viewChoice.getItems().setAll(DataViewMode.values());
        viewChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (model == null || newValue == null || newValue == model.getCustomersViewMode()) {
                return;
            }
            model.setCustomersViewMode(newValue);
            reloadCustomers();
        });

        updatePlaceholder();

        if (model != null) {
            bindModel();
        }
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        if (customersTable == null) {
            return;
        }
        bindModel();
    }

    private void bindModel() {
        if (model == null) {
            updatePlaceholder();
            return;
        }

        customersTable.setItems(model.customersView());
        model.customersView().comparatorProperty().bind(customersTable.comparatorProperty());
        model.customers().removeListener(customersListener);
        model.customers().addListener(customersListener);

        if (!modelListenersBound) {
            model.selectedEventProperty().addListener((obs, oldValue, newValue) -> {
                if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                    reloadCustomers();
                }
            });
            model.currentEventIdProperty().addListener((obs, oldValue, newValue) -> {
                if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                    reloadCustomers();
                }
            });
            model.customersViewModeProperty().addListener((obs, oldValue, newValue) -> {
                if (viewChoice.getValue() != newValue) {
                    viewChoice.setValue(newValue);
                }
                updatePlaceholder();
            });
            modelListenersBound = true;
        }

        if (viewChoice.getValue() != model.getCustomersViewMode()) {
            viewChoice.setValue(model.getCustomersViewMode());
        }

        updateShowDeletedButtonText();
        updatePlaceholder();
        reloadCustomers();
    }

    @FXML
    private void onToggleShowDeletedCustomers() {
        if (model == null) {
            return;
        }
        model.setShowDeletedCustomerTickets(!model.isShowDeletedCustomerTickets());
        updateShowDeletedButtonText();
        reloadCustomers();
    }

    private void reloadCustomers() {
        customersTable.getSelectionModel().clearSelection();
        if (model != null) {
            model.customers().clear();
            model.setSelectedCustomer(null);
        }
        updatePlaceholder();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                    model.loadCustomersForEvent(model.getCurrentEventId());
                } else {
                    model.loadAllCustomers();
                }
                return null;
            }
        };

        task.setOnFailed(workerStateEvent -> {
            statusBanner.showFailed();
            DialogUtils.showError("Load Customers", null,
                    task.getException() == null ? "Unable to load customers." : task.getException().getMessage());
        });

        new Thread(task, "load-customers-task").start();
    }

    private void restoreSelection() {
        if (model == null || customersTable == null) {
            return;
        }

        Customer selected = model.getSelectedCustomer();
        if (selected == null || selected.getId() == null) {
            return;
        }

        for (Customer customer : model.customersView()) {
            if (customer != null && selected.getId().equals(customer.getId())) {
                customersTable.getSelectionModel().select(customer);
                customersTable.scrollTo(customer);
                model.setSelectedCustomer(customer);
                return;
            }
        }
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedCustomerTickets()
                ? "Hide Deleted Tickets"
                : "Show Deleted Tickets");
    }

    private void updatePlaceholder() {
        if (placeholderLabel == null || customersTable == null || model == null) {
            return;
        }
        if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
            placeholderLabel.setText("Select an event to view customers.");
            return;
        }
        placeholderLabel.setText("No customers found.");
    }
}
