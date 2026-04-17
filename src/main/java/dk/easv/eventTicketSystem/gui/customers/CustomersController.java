package dk.easv.eventTicketSystem.gui.customers;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.DataViewMode;
import dk.easv.eventTicketSystem.gui.model.SearchScope;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.StatusBanner;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
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
    private ChoiceBox<DataViewMode> viewChoice;

    private final Label placeholderLabel = new Label("No customers found.");
    private final ListChangeListener<Customer> customersListener = change -> {
        updatePlaceholder();
        restoreSelection();
    };

    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;
    private boolean suppressSelectionEvents;
    private boolean reloadPending = true;
    private String lastLoadKey;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        placeholderLabel.getStyleClass().add("muted-text");

        colCustomerTableName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colCustomerTableEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());

        customersTable.setPlaceholder(placeholderLabel);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCustomer, newCustomer) -> {
            if (suppressSelectionEvents) {
                return;
            }
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
            requestReload(true);
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
                    requestReload(false);
                }
            });
            model.currentEventIdProperty().addListener((obs, oldValue, newValue) -> {
                if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT) {
                    requestReload(false);
                }
            });
            model.customersViewModeProperty().addListener((obs, oldValue, newValue) -> {
                if (viewChoice.getValue() != newValue) {
                    viewChoice.setValue(newValue);
                }
                updatePlaceholder();
                requestReload(false);
            });
            model.activeSearchScopeProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue == SearchScope.CUSTOMERS) {
                    requestReload(false);
                }
            });
            modelListenersBound = true;
        }

        if (viewChoice.getValue() != model.getCustomersViewMode()) {
            viewChoice.setValue(model.getCustomersViewMode());
        }

        updatePlaceholder();
        requestReload(false);
    }

    private void reloadCustomers(String loadKey) {
        suppressSelectionEvents = true;
        customersTable.getSelectionModel().clearSelection();
        if (model != null) {
            model.customers().clear();
            if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
                model.setSelectedCustomer(null);
            }
        }
        suppressSelectionEvents = false;
        updatePlaceholder();
        reloadPending = false;
        lastLoadKey = loadKey;

        if (model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
            return;
        }

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
            reloadPending = true;
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
                suppressSelectionEvents = true;
                customersTable.getSelectionModel().select(customer);
                customersTable.scrollTo(customer);
                suppressSelectionEvents = false;
                model.setSelectedCustomer(customer);
                return;
            }
        }

        suppressSelectionEvents = true;
        customersTable.getSelectionModel().clearSelection();
        suppressSelectionEvents = false;
        model.setSelectedCustomer(null);
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

    private void requestReload(boolean force) {
        if (model == null || customersTable == null) {
            return;
        }

        String loadKey = buildLoadKey();
        if (!force && !reloadPending && loadKey.equals(lastLoadKey)) {
            return;
        }

        reloadCustomers(loadKey);
    }

    private String buildLoadKey() {
        if (model == null) {
            return "customers:none";
        }
        long eventId = model.getCustomersViewMode() == DataViewMode.SELECTED_EVENT
                ? model.getCurrentEventId()
                : -1L;
        return model.getCustomersViewMode() + "|" + eventId;
    }
}
