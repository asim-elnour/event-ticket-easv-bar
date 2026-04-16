package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.bll.CustomerLogic;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.util.CustomerValidationRules;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerModel {

    private final CustomerLogic customerLogic = new CustomerLogic();
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final SortedList<Customer> customersSorted = new SortedList<>(customers);
    private final AtomicLong customersRequestVersion = new AtomicLong(0);

    private boolean showDeletedCustomerTickets = true;
    private SearchModel.SearchState customerSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");

    public ObservableList<Customer> customers() {
        return customers;
    }

    public SortedList<Customer> customersView() {
        return customersSorted;
    }

    public boolean isShowDeletedCustomerTickets() {
        return showDeletedCustomerTickets;
    }

    public void setShowDeletedCustomerTickets(boolean showDeletedCustomerTickets) {
        this.showDeletedCustomerTickets = showDeletedCustomerTickets;
    }

    public void applySearch(SearchModel.SearchState state) {
        customerSearchState = state == null
                ? new SearchModel.SearchState(SearchModel.COLUMN_ALL, "")
                : state;
    }

    public void loadCustomersForEvent(long eventId) throws CustomerException {
        long requestVersion = customersRequestVersion.incrementAndGet();
        if (eventId <= 0) {
            Platform.runLater(() -> {
                if (requestVersion == customersRequestVersion.get()) {
                    customers.clear();
                }
            });
            return;
        }

        List<Customer> loadedCustomers = customerLogic.getCustomersForEvent(eventId, showDeletedCustomerTickets);
        setCustomers(requestVersion, filterAndSort(loadedCustomers));
    }

    public void loadAllCustomers() throws CustomerException {
        long requestVersion = customersRequestVersion.incrementAndGet();
        List<Customer> loadedCustomers = customerLogic.getAllCustomers(showDeletedCustomerTickets);
        setCustomers(requestVersion, filterAndSort(loadedCustomers));
    }

    private void setCustomers(long requestVersion, List<Customer> loadedCustomers) {
        Platform.runLater(() -> {
            if (requestVersion == customersRequestVersion.get()) {
                customers.setAll(loadedCustomers);
            }
        });
    }

    private List<Customer> filterAndSort(List<Customer> loadedCustomers) {
        if (loadedCustomers == null || loadedCustomers.isEmpty()) {
            return List.of();
        }

        String columnKey = customerSearchState.columnKey();
        String query = customerSearchState.query();

        return loadedCustomers.stream()
                .filter(customer -> matchesCustomer(customer, columnKey, query))
                .map(Customer::copy)
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Customer::getEmail, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Customer::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private boolean matchesCustomer(Customer customer, String columnKey, String query) {
        if (customer == null) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }

        String normalizedColumn = columnKey == null ? SearchModel.COLUMN_ALL : columnKey;
        return switch (normalizedColumn) {
            case SearchModel.COLUMN_NAME -> CustomerValidationRules.matchesSearch(query, customer.getName());
            case SearchModel.COLUMN_EMAIL -> CustomerValidationRules.matchesSearch(query, customer.getEmail());
            case SearchModel.COLUMN_CUSTOMER, SearchModel.COLUMN_ALL ->
                    CustomerValidationRules.matchesSearch(query, customer.getName(), customer.getEmail());
            default -> CustomerValidationRules.matchesSearch(query, customer.getName(), customer.getEmail());
        };
    }
}
