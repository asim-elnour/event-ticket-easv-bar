package dk.easv.eventTicketSystem.gui.customers;

import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.StatusBanner;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomersController implements ModelAware {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<CustomerRow> customersTable;
    @FXML
    private TableColumn<CustomerRow, String> colCustomerTableName;
    @FXML
    private TableColumn<CustomerRow, String> colCustomerTableEmail;
    @FXML
    private TableColumn<CustomerRow, Number> colCustomerTableTickets;
    @FXML
    private Button showDeletedButton;

    private final ObservableList<CustomerRow> customerRows = FXCollections.observableArrayList();
    private final SortedList<CustomerRow> customerRowsView = new SortedList<>(customerRows);
    private final ListChangeListener<Ticket> ticketsListener = change -> {
        updateShowDeletedButtonText();
        refreshCustomerTable();
    };

    private ObservableList<Ticket> observedTickets;
    private AppModel model;
    private StatusBanner statusBanner;
    private String selectedCustomerKey = "";

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);

        colCustomerTableName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colCustomerTableEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());
        colCustomerTableTickets.setCellValueFactory(cd -> cd.getValue().ticketCountProperty());

        customersTable.setItems(customerRowsView);
        customerRowsView.comparatorProperty().bind(customersTable.comparatorProperty());
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCustomer, newCustomer) -> {
            if (newCustomer == null) {
                return;
            }
            selectedCustomerKey = customerKey(newCustomer);
            selectRepresentativeTicketForCustomer(newCustomer);
        });

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
            customerRows.clear();
            return;
        }

        if (observedTickets != null) {
            observedTickets.removeListener(ticketsListener);
        }

        observedTickets = model.ticketsView();
        observedTickets.addListener(ticketsListener);

        updateShowDeletedButtonText();
        refreshCustomerTable();
    }

    @FXML
    private void onToggleShowDeletedCustomers() {
        if (model == null) {
            return;
        }
        model.setShowDeletedTickets(!model.isShowDeletedTickets());
        updateShowDeletedButtonText();
        reloadAllTickets();
    }

    private void reloadAllTickets() {
        statusBanner.showSaving();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.loadAllTickets();
                return null;
            }
        };

        task.setOnSucceeded(workerStateEvent -> statusBanner.showSaved());
        task.setOnFailed(workerStateEvent -> {
            statusBanner.showFailed();
            DialogUtils.showError("Load Customers", null,
                    task.getException() == null ? "Unable to load customers." : task.getException().getMessage());
        });

        new Thread(task, "load-customers-task").start();
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedTickets() ? "Hide Deleted Tickets" : "Show Deleted Tickets");
    }

    private void refreshCustomerTable() {
        if (observedTickets == null) {
            customerRows.clear();
            return;
        }

        Map<String, CustomerRow> groupedCustomers = new LinkedHashMap<>();
        for (Ticket ticket : observedTickets) {
            if (ticket == null) {
                continue;
            }

            String name = cleanText(ticket.getCustomerName());
            String email = cleanText(ticket.getCustomerEmail());
            String key = normalizeKey(email.isBlank() ? name : email);
            if (key.isBlank()) {
                continue;
            }

            CustomerRow row = groupedCustomers.get(key);
            if (row == null) {
                row = new CustomerRow(name, email);
                groupedCustomers.put(key, row);
            } else {
                row.merge(name, email);
            }
            row.incrementTicketCount();
        }

        List<CustomerRow> sortedCustomers = new ArrayList<>(groupedCustomers.values());
        sortedCustomers.sort(
                Comparator.comparing(CustomerRow::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CustomerRow::getEmail, String.CASE_INSENSITIVE_ORDER)
        );
        customerRows.setAll(sortedCustomers);
        restoreCustomerSelection();
    }

    private void restoreCustomerSelection() {
        if (customersTable == null || selectedCustomerKey.isBlank()) {
            return;
        }

        for (CustomerRow customer : customerRowsView) {
            if (selectedCustomerKey.equals(customerKey(customer))) {
                customersTable.getSelectionModel().select(customer);
                customersTable.scrollTo(customer);
                return;
            }
        }
    }

    private void selectRepresentativeTicketForCustomer(CustomerRow customer) {
        if (model == null || observedTickets == null || customer == null) {
            return;
        }

        String key = customerKey(customer);
        for (Ticket ticket : observedTickets) {
            if (ticket != null && key.equals(customerKey(ticket))) {
                model.setSelectedTicket(ticket);
                return;
            }
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeKey(String value) {
        return cleanText(value).toLowerCase();
    }

    private String customerKey(CustomerRow customer) {
        if (customer == null) {
            return "";
        }
        String email = cleanText(customer.getEmail());
        return normalizeKey(email.isBlank() ? customer.getName() : email);
    }

    private String customerKey(Ticket ticket) {
        if (ticket == null) {
            return "";
        }
        String email = cleanText(ticket.getCustomerEmail());
        return normalizeKey(email.isBlank() ? ticket.getCustomerName() : email);
    }

    public static final class CustomerRow {
        private final SimpleStringProperty name = new SimpleStringProperty("");
        private final SimpleStringProperty email = new SimpleStringProperty("");
        private final SimpleIntegerProperty ticketCount = new SimpleIntegerProperty(0);

        public CustomerRow(String name, String email) {
            setName(name);
            setEmail(email);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String value) {
            name.set(value == null ? "" : value.trim());
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getEmail() {
            return email.get();
        }

        public void setEmail(String value) {
            email.set(value == null ? "" : value.trim());
        }

        public SimpleStringProperty emailProperty() {
            return email;
        }

        public Number getTicketCount() {
            return ticketCount.get();
        }

        public SimpleIntegerProperty ticketCountProperty() {
            return ticketCount;
        }

        public void incrementTicketCount() {
            ticketCount.set(ticketCount.get() + 1);
        }

        public void merge(String candidateName, String candidateEmail) {
            if (getName().isBlank() && candidateName != null && !candidateName.isBlank()) {
                setName(candidateName);
            }
            if (getEmail().isBlank() && candidateEmail != null && !candidateEmail.isBlank()) {
                setEmail(candidateEmail);
            }
        }
    }
}
