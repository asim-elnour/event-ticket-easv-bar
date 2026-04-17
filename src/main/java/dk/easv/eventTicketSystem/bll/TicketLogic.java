package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import dk.easv.eventTicketSystem.util.CustomerValidationRules;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class TicketLogic {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final CustomerLogic customerLogic;
    private final Clock clock;

    public TicketLogic() {
        this(RepositoryProvider.tickets(), RepositoryProvider.events(), new CustomerLogic(), Clock.systemDefaultZone());
    }

    public TicketLogic(TicketRepository ticketRepository) {
        this(ticketRepository, RepositoryProvider.events(), new CustomerLogic(), Clock.systemDefaultZone());
    }

    public TicketLogic(TicketRepository ticketRepository, EventRepository eventRepository) {
        this(ticketRepository, eventRepository, new CustomerLogic(), Clock.systemDefaultZone());
    }

    public TicketLogic(TicketRepository ticketRepository,
                       EventRepository eventRepository,
                       CustomerLogic customerLogic) {
        this(ticketRepository, eventRepository, customerLogic, Clock.systemDefaultZone());
    }

    public TicketLogic(TicketRepository ticketRepository,
                       EventRepository eventRepository,
                       CustomerLogic customerLogic,
                       Clock clock) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.customerLogic = customerLogic;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
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
                                              boolean includeRefunded) throws TicketException {
        return ticketRepository.searchTicketsForEvent(eventId, columnKey, query, includeRefunded);
    }

    public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeRefunded) throws TicketException {
        return ticketRepository.searchAllTickets(columnKey, query, includeRefunded);
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

        Customer customer = resolveCustomer(normalizedCustomerName, normalizedCustomerEmail);
        return ticketRepository.addTicket(eventId, ticketCategoryId, customer.getId(), code);
    }

    public Ticket getTicketById(long ticketId) throws TicketException {
        return ticketRepository.getTicketById(ticketId);
    }

    public Ticket refundTicketById(long ticketId) throws TicketException {
        if (ticketId <= 0) {
            throw new TicketException("Please select a valid ticket first.", null);
        }

        Ticket ticket = ticketRepository.getTicketById(ticketId);
        if (ticket.isRefunded()) {
            throw new TicketException("This ticket is already refunded.", null);
        }
        if (ticket.isRedeemed()) {
            throw new TicketException("Redeemed tickets cannot be refunded.", null);
        }
        validateRefundWindow(ticket);
        return ticketRepository.refundTicketById(ticketId);
    }

    public Ticket redeemTicketById(long ticketId) throws TicketException {
        if (ticketId <= 0) {
            throw new TicketException("Please select a valid ticket first.", null);
        }
        Ticket ticket = ticketRepository.getTicketById(ticketId);
        if (ticket.isRefunded()) {
            throw new TicketException("Refunded tickets cannot be redeemed.", null);
        }
        if (ticket.isRedeemed()) {
            throw new TicketException("This ticket is already redeemed.", null);
        }
        validateRedeemWindow(ticket);
        return ticketRepository.redeemTicketById(ticketId);
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
        String normalized = CustomerValidationRules.normalizeRequired(value);
        if (normalized.isEmpty()) {
            throw new TicketException(fieldName + " is required.", null);
        }
        if (normalized.length() > CustomerValidationRules.MAX_TEXT_LENGTH) {
            throw new TicketException(fieldName + " is too long.", null);
        }
        return normalized;
    }

    private String safeTicketTypeName(TicketCategory category) {
        String name = category == null ? null : CustomerValidationRules.normalizeRequired(category.getName());
        return name == null || name.isEmpty() ? "selected ticket type" : name;
    }

    private Customer resolveCustomer(String customerName, String customerEmail) throws TicketException {
        if (!CustomerValidationRules.isValidEmail(customerEmail)) {
            throw new TicketException("Customer email must be a valid email address.", null);
        }

        try {
            return customerLogic.resolveOrCreateCustomer(customerName, customerEmail);
        } catch (CustomerException e) {
            throw new TicketException(e.getMessage(), e);
        }
    }

    private void validateRefundWindow(Ticket ticket) throws TicketException {
        Event event = requireEventForTicketAction(ticket, "Refund");
        LocalDateTime start = event.getStartTime();
        if (start == null) {
            throw new TicketException("Refund is unavailable because the event start time is missing.", null);
        }
        LocalDateTime cutoff = start.minusMinutes(30);
        if (!now().isBefore(cutoff)) {
            throw new TicketException("Tickets can only be refunded more than 30 minutes before the event starts.", null);
        }
    }

    private void validateRedeemWindow(Ticket ticket) throws TicketException {
        Event event = requireEventForTicketAction(ticket, "Redeem");
        LocalDateTime start = event.getStartTime();
        if (start == null) {
            throw new TicketException("Redeem is unavailable because the event start time is missing.", null);
        }

        LocalDateTime now = now();
        LocalDateTime redeemWindowStart = start.minusMinutes(30);
        if (now.isBefore(redeemWindowStart)) {
            throw new TicketException("Tickets can only be redeemed from 30 minutes before the event starts.", null);
        }

        LocalDateTime end = event.getEndTime();
        if (end != null && now.isAfter(end)) {
            throw new TicketException("This event has already ended. Tickets can no longer be redeemed.", null);
        }
    }

    private Event requireEventForTicketAction(Ticket ticket, String actionName) throws TicketException {
        if (ticket == null || ticket.getEventId() == null || ticket.getEventId() <= 0) {
            throw new TicketException(actionName + " is unavailable because the event could not be resolved.", null);
        }
        Event event = getEvent(ticket.getEventId());
        if (event.isDeleted()) {
            throw new TicketException(actionName + " is unavailable because this event is deleted.", null);
        }
        return event;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
