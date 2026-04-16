package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.CustomerSummary;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.bll.TicketLogic;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerModel {

    private final TicketLogic ticketLogic = new TicketLogic();
    private final ObservableList<CustomerSummary> customers = FXCollections.observableArrayList();
    private final SortedList<CustomerSummary> customersSorted = new SortedList<>(customers);
    private final AtomicLong customersRequestVersion = new AtomicLong(0);

    private boolean showDeletedCustomerTickets = true;
    private SearchModel.SearchState customerSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");

    public ObservableList<CustomerSummary> customers() {
        return customers;
    }

    public SortedList<CustomerSummary> customersView() {
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

    public void loadCustomersForEvent(long eventId) throws TicketException {
        long requestVersion = customersRequestVersion.incrementAndGet();
        if (eventId <= 0) {
            Platform.runLater(() -> {
                if (requestVersion == customersRequestVersion.get()) {
                    customers.clear();
                }
            });
            return;
        }

        List<Ticket> loadedTickets = ticketLogic.searchTicketsForEvent(
                eventId,
                customerSearchState.columnKey(),
                customerSearchState.query(),
                showDeletedCustomerTickets
        );
        setCustomers(requestVersion, summarizeCustomers(loadedTickets));
    }

    public void loadAllCustomers() throws TicketException {
        long requestVersion = customersRequestVersion.incrementAndGet();
        List<Ticket> loadedTickets = ticketLogic.searchAllTickets(
                customerSearchState.columnKey(),
                customerSearchState.query(),
                showDeletedCustomerTickets
        );
        setCustomers(requestVersion, summarizeCustomers(loadedTickets));
    }

    private void setCustomers(long requestVersion, List<CustomerSummary> loadedCustomers) {
        Platform.runLater(() -> {
            if (requestVersion == customersRequestVersion.get()) {
                customers.setAll(loadedCustomers);
            }
        });
    }

    private List<CustomerSummary> summarizeCustomers(List<Ticket> tickets) {
        Map<String, CustomerSummary> groupedCustomers = new LinkedHashMap<>();
        if (tickets == null) {
            return List.of();
        }

        for (Ticket ticket : tickets) {
            if (ticket == null) {
                continue;
            }

            String name = cleanText(ticket.getCustomerName());
            String email = cleanText(ticket.getCustomerEmail());
            String key = normalizeKey(email.isBlank() ? name : email);
            if (key.isBlank()) {
                continue;
            }

            CustomerSummary summary = groupedCustomers.computeIfAbsent(key, ignored -> new CustomerSummary(key, name, email));
            summary.includeTicket(ticket);
        }

        List<CustomerSummary> sortedCustomers = new ArrayList<>(groupedCustomers.values());
        sortedCustomers.sort(
                Comparator.comparing(CustomerSummary::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CustomerSummary::getEmail, String.CASE_INSENSITIVE_ORDER)
        );
        return sortedCustomers;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeKey(String value) {
        return cleanText(value).toLowerCase();
    }
}
