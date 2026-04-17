package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.util.EventValidationRules;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EventLogic {

    private final EventRepository eventRepository;

    public EventLogic() {
        this(RepositoryProvider.events());
    }

    public EventLogic(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<Event> getAllEvents() throws EventException {
        return eventRepository.getAllEvents();
    }

    public List<Event> getEventsForCoordinator(long coordinatorId) throws EventException {
        return eventRepository.getEventsForCoordinator(coordinatorId);
    }

    public List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) throws EventException {
        return eventRepository.searchAllEvents(columnKey, query, includeDeleted);
    }

    public List<Event> searchEventsForCoordinator(long coordinatorId,
                                                  String columnKey,
                                                  String query,
                                                  boolean includeDeleted) throws EventException {
        return eventRepository.searchEventsForCoordinator(coordinatorId, columnKey, query, includeDeleted);
    }

    public Event addEvent(Event event) throws EventException {
        normalizeEvent(event);
        validateEvent(event);
        return eventRepository.addEvent(event);
    }

    public void updateEvent(Event event) throws EventException {
        normalizeEvent(event);
        validateEvent(event);
        Event updated = eventRepository.updateEvent(event);
        event.restoreFrom(updated);
    }

    public void deleteEvent(long eventId, long coordinatorId) throws EventException {
        setEventDeleted(eventId, coordinatorId, true);
    }

    public void setEventDeleted(long eventId, long coordinatorId, boolean deleted) throws EventException {
        if (eventId <= 0) {
            throw new EventException("Valid event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }
        eventRepository.setEventDeleted(eventId, coordinatorId, deleted);
    }

    private void normalizeEvent(Event event) {
        if (event == null) {
            return;
        }
        event.setName(EventValidationRules.normalizeRequired(event.getName()));
        event.setLocation(EventValidationRules.normalizeRequired(event.getLocation()));
        event.setLocationGuidance(EventValidationRules.normalizeOptional(event.getLocationGuidance()));
        event.setNotes(EventValidationRules.normalizeOptional(event.getNotes()));
    }

    private void validateEvent(Event event) throws EventException {
        if (event == null) {
            throw new EventException("Event cannot be null.", EventException.ErrorType.VALIDATION_ERROR);
        }

        validateRequiredText("Name", event.getName());
        validateRequiredText("Location", event.getLocation());

        LocalDateTime start = event.getStartTime();
        if (start == null) {
            throw new EventException("Start date and time are required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        LocalDateTime end = event.getEndTime();
        if (end != null && !end.isAfter(start)) {
            throw new EventException("End date and time must be after the start date and time.",
                    EventException.ErrorType.VALIDATION_ERROR);
        }

        List<TicketCategory> ticketTypes = event.getTicketTypes();
        if (ticketTypes == null || ticketTypes.isEmpty()) {
            throw new EventException("At least one ticket type is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        for (TicketCategory ticketType : ticketTypes) {
            validateTicketType(ticketType);
        }

        int activeTicketTypes = EventValidationRules.countActiveTicketTypes(ticketTypes);
        if (activeTicketTypes == 0) {
            throw new EventException("At least one active ticket type is required.",
                    EventException.ErrorType.VALIDATION_ERROR);
        }
    }

    private void validateTicketType(TicketCategory ticketType) throws EventException {
        if (ticketType == null) {
            throw new EventException("Ticket type cannot be empty.", EventException.ErrorType.VALIDATION_ERROR);
        }

        String name = EventValidationRules.normalizeRequired(ticketType.getName());
        ticketType.setName(name);
        if (name.isEmpty()) {
            throw new EventException("Ticket type name is required.", EventException.ErrorType.VALIDATION_ERROR);
        }
        if (name.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            throw new EventException("Ticket type name is too long.", EventException.ErrorType.VALIDATION_ERROR);
        }

        BigDecimal price = ticketType.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new EventException("Ticket price must be zero or higher.", EventException.ErrorType.VALIDATION_ERROR);
        }

        Integer seats = ticketType.getSeatCount();
        if (seats == null || seats < EventValidationRules.MIN_SEAT_COUNT) {
            throw new EventException("Ticket seats must be at least " + EventValidationRules.MIN_SEAT_COUNT + ".",
                    EventException.ErrorType.VALIDATION_ERROR);
        }
    }

    private void validateRequiredText(String fieldName, String value) throws EventException {
        if (value == null || value.isBlank()) {
            throw new EventException(fieldName + " is required.", EventException.ErrorType.VALIDATION_ERROR);
        }
        if (value.length() > EventValidationRules.MAX_TEXT_LENGTH) {
            throw new EventException(fieldName + " is too long.", EventException.ErrorType.VALIDATION_ERROR);
        }
    }
}
