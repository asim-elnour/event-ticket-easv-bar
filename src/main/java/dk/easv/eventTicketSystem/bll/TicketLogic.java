package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.TicketException;

import java.util.List;

public class TicketLogic {

    private final TicketRepository ticketRepository = RepositoryProvider.tickets();

    public List<Ticket> getTicketsForEvent(long eventId) throws TicketException {
        return ticketRepository.getTicketsForEvent(eventId);
    }

    public List<Ticket> getAllTickets() throws TicketException {
        return ticketRepository.getAllTickets();
    }

    public List<Ticket> searchTicketsForEvent(long eventId,
                                              String columnKey,
                                              String query,
                                              boolean includeDeleted) throws TicketException {
        return ticketRepository.searchTicketsForEvent(eventId, columnKey, query, includeDeleted);
    }

    public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeDeleted) throws TicketException {
        return ticketRepository.searchAllTickets(columnKey, query, includeDeleted);
    }

    public Ticket addTicket(long eventId,
                            Long ticketCategoryId,
                            String customerName,
                            String customerEmail,
                            String code) throws TicketException {
        if (eventId <= 0) {
            throw new TicketException("Event is required when creating a ticket.", null);
        }

        if (customerName == null || customerName.isBlank() || customerEmail == null || customerEmail.isBlank()) {
            throw new TicketException("Customer name and email are required.", null);
        }

        return ticketRepository.addTicket(eventId, ticketCategoryId, customerName, customerEmail, code);
    }

    public Ticket setTicketDeletedState(long ticketId, boolean deleted) throws TicketException {
        return ticketRepository.setTicketDeletedState(ticketId, deleted);
    }

    public Ticket redeemTicketById(long ticketId) throws TicketException {
        return ticketRepository.redeemTicketById(ticketId);
    }
}
