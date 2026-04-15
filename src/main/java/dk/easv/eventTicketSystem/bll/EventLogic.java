package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.exceptions.EventException;

import java.math.BigDecimal;
import java.util.List;

public class EventLogic {

    private final EventRepository eventRepository = RepositoryProvider.events();

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
        validateEvent(event);
        return eventRepository.addEvent(event);
    }

    public void updateEvent(Event event) throws EventException {
        validateEvent(event);
        Event updated = eventRepository.updateEvent(event);
        event.restoreFrom(updated);
    }

    public void deleteEvent(long eventId, long coordinatorId) throws EventException {
        setEventDeleted(eventId, coordinatorId, true);
    }

    public void setEventDeleted(long eventId, long coordinatorId, boolean deleted) throws EventException {
        eventRepository.setEventDeleted(eventId, coordinatorId, deleted);
    }

    private void validateEvent(Event event) throws EventException {
        if (event == null) {
            throw new EventException("Event cannot be null", EventException.ErrorType.VALIDATION_ERROR);
        }

        if (event.getCapacity() < 0) {
            throw new EventException("Capacity must be zero or higher.", EventException.ErrorType.VALIDATION_ERROR);
        }

        if (event.getTicketTypes() == null || event.getTicketTypes().isEmpty()) {
            throw new EventException("At least one ticket type is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        for (TicketCategory ticketType : event.getTicketTypes()) {
            validateTicketType(ticketType);
        }
    }

    private void validateTicketType(TicketCategory ticketType) throws EventException {
        if (ticketType == null) {
            throw new EventException("Ticket type cannot be empty.", EventException.ErrorType.VALIDATION_ERROR);
        }

        String name = ticketType.getName();
        if (name == null || name.isBlank()) {
            throw new EventException("Ticket type name is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        BigDecimal price = ticketType.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new EventException("Ticket price must be zero or higher.", EventException.ErrorType.VALIDATION_ERROR);
        }

        Integer seats = ticketType.getSeatCount();
        if (seats == null || seats <= 0) {
            throw new EventException("Ticket seats/number must be greater than 0.", EventException.ErrorType.VALIDATION_ERROR);
        }
    }
}
