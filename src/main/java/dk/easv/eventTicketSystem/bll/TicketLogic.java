package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import dk.easv.eventTicketSystem.util.EventValidationRules;

import java.util.List;

public class TicketLogic {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    public TicketLogic() {
        this(RepositoryProvider.tickets(), RepositoryProvider.events());
    }

    public TicketLogic(TicketRepository ticketRepository) {
        this(ticketRepository, RepositoryProvider.events());
    }

    public TicketLogic(TicketRepository ticketRepository, EventRepository eventRepository) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

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
        if (ticketCategoryId == null || ticketCategoryId <= 0) {
            throw new TicketException("Ticket type is required.", null);
        }

        String normalizedCustomerName = normalizeRequired(customerName, "Customer name");
        String normalizedCustomerEmail = normalizeRequired(customerEmail, "Customer email");

        Event event = getEvent(eventId);
        if (event.isDeleted()) {
            throw new TicketException("Cannot issue tickets for a deleted event.", null);
        }

        TicketCategory category = requireActiveCategory(event, ticketCategoryId,
                "Selected ticket type is not available for this event.");
        ensureCategoryHasSeats(eventId, category);

        return ticketRepository.addTicket(eventId, ticketCategoryId, normalizedCustomerName, normalizedCustomerEmail, code);
    }

    public Ticket getTicketById(long ticketId) throws TicketException {
        return ticketRepository.getTicketById(ticketId);
    }

    public Ticket setTicketDeletedState(long ticketId, boolean deleted) throws TicketException {
        if (ticketId <= 0) {
            throw new TicketException("Please select a valid ticket first.", null);
        }

        if (!deleted) {
            validateTicketRestore(ticketId);
        }
        return ticketRepository.setTicketDeletedState(ticketId, deleted);
    }

    public Ticket redeemTicketById(long ticketId) throws TicketException {
        return ticketRepository.redeemTicketById(ticketId);
    }

    private void validateTicketRestore(long ticketId) throws TicketException {
        Ticket ticket = ticketRepository.getTicketById(ticketId);
        if (!ticket.isDeleted()) {
            return;
        }
        if (ticket.getEventId() == null || ticket.getEventId() <= 0) {
            throw new TicketException("Cannot restore a ticket without an event.", null);
        }
        if (ticket.getTicketCategoryId() == null || ticket.getTicketCategoryId() <= 0) {
            throw new TicketException("Cannot restore a ticket without a ticket type.", null);
        }

        Event event = getEvent(ticket.getEventId());
        if (event.isDeleted()) {
            throw new TicketException("Cannot restore a ticket for a deleted event.", null);
        }

        TicketCategory category = requireActiveCategory(event, ticket.getTicketCategoryId(),
                "Cannot restore this ticket because its ticket type is deleted.");
        ensureCategoryHasSeats(ticket.getEventId(), category);
    }

    private Event getEvent(long eventId) throws TicketException {
        try {
            return eventRepository.getEventById(eventId);
        } catch (EventException e) {
            throw new TicketException("Unable to load the selected event.", e);
        }
    }

    private TicketCategory requireActiveCategory(Event event,
                                                 long ticketCategoryId,
                                                 String message) throws TicketException {
        for (TicketCategory category : event.getTicketTypes()) {
            if (category == null || category.getId() == null) {
                continue;
            }
            if (category.getId().equals(ticketCategoryId) && !category.isDeleted()) {
                return category;
            }
        }
        throw new TicketException(message, null);
    }

    private void ensureCategoryHasSeats(long eventId, TicketCategory category) throws TicketException {
        int activeTickets = ticketRepository.countActiveTicketsForTicketCategory(eventId, category.getId());
        int seatCount = category.getSeatCount() == null ? 0 : category.getSeatCount();
        if (activeTickets >= seatCount) {
            throw new TicketException("No seats are left for ticket type '" + safeTicketTypeName(category) + "'.", null);
        }
    }

    private String normalizeRequired(String value, String fieldName) throws TicketException {
        String normalized = EventValidationRules.normalizeRequired(value);
        if (normalized.isEmpty()) {
            throw new TicketException(fieldName + " is required.", null);
        }
        if (normalized.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            throw new TicketException(fieldName + " is too long.", null);
        }
        return normalized;
    }

    private String safeTicketTypeName(TicketCategory category) {
        String name = category == null ? null : EventValidationRules.normalizeRequired(category.getName());
        return name == null || name.isEmpty() ? "selected ticket type" : name;
    }
}
