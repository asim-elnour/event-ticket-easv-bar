package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.bll.TicketLogic;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TicketModel {

    private final TicketLogic ticketLogic = new TicketLogic();
    private final ObservableList<Ticket> tickets = FXCollections.observableArrayList();
    private final SortedList<Ticket> ticketsSorted = new SortedList<>(tickets);
    private final AtomicLong ticketsRequestVersion = new AtomicLong(0);

    private boolean showDeletedTickets = true;
    private SearchModel.SearchState ticketSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");

    public ObservableList<Ticket> tickets() {
        return tickets;
    }

    public SortedList<Ticket> ticketsView() {
        return ticketsSorted;
    }

    public boolean isShowDeletedTickets() {
        return showDeletedTickets;
    }

    public void setShowDeletedTickets(boolean showDeletedTickets) {
        this.showDeletedTickets = showDeletedTickets;
    }

    public void applySearch(SearchModel.SearchState state) {
        ticketSearchState = state == null
                ? new SearchModel.SearchState(SearchModel.COLUMN_ALL, "")
                : state;
    }

    public void loadTicketsForEvent(long eventId) throws TicketException {
        long requestVersion = ticketsRequestVersion.incrementAndGet();
        if (eventId <= 0) {
            Platform.runLater(() -> {
                if (requestVersion == ticketsRequestVersion.get()) {
                    tickets.clear();
                }
            });
            return;
        }

        List<Ticket> loaded = ticketLogic.searchTicketsForEvent(
                eventId,
                ticketSearchState.columnKey(),
                ticketSearchState.query(),
                showDeletedTickets
        );
        Platform.runLater(() -> {
            if (requestVersion == ticketsRequestVersion.get()) {
                tickets.setAll(loaded);
            }
        });
    }

    public void loadAllTickets() throws TicketException {
        long requestVersion = ticketsRequestVersion.incrementAndGet();
        List<Ticket> loaded = ticketLogic.searchAllTickets(
                ticketSearchState.columnKey(),
                ticketSearchState.query(),
                showDeletedTickets
        );
        Platform.runLater(() -> {
            if (requestVersion == ticketsRequestVersion.get()) {
                tickets.setAll(loaded);
            }
        });
    }

    public Ticket addTicket(Event event,
                            Long ticketCategoryId,
                            String customerName,
                            String customerEmail,
                            String code) throws TicketException {
        if (event == null || event.getId() == null) {
            throw new TicketException("Event is required when creating a ticket.", null);
        }

        Ticket created = ticketLogic.addTicket(event.getId(), ticketCategoryId, customerName, customerEmail, code);
        if (created.getEventName() == null || created.getEventName().isBlank()) {
            created.setEventName(event.getName());
        }
        return created;
    }

    public void setTicketDeleted(Ticket ticket, boolean deleted) throws TicketException {
        if (ticket == null || ticket.getId() == null) {
            throw new TicketException("Please select a valid ticket first.", null);
        }

        ticketLogic.setTicketDeletedState(ticket.getId(), deleted);
    }

    public void redeemTicket(Ticket ticket) throws TicketException {
        if (ticket == null || ticket.getId() == null) {
            throw new TicketException("Please select a valid ticket first.", null);
        }
        if (ticket.isDeleted()) {
            throw new TicketException("Deleted tickets cannot be redeemed.", null);
        }
        ticketLogic.redeemTicketById(ticket.getId());
    }
}
