package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.util.EventValidationRules;
import dk.easv.eventTicketSystem.util.SessionManager;

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
        User coordinator = requireActiveCoordinator("Only event coordinators can create or edit events.");
        normalizeEvent(event);
        validateEvent(event);
        event.setCreatedByUserId(coordinator.getId());
        event.setCoordinatorId(coordinator.getId());
        return eventRepository.addEvent(event, coordinator.getId());
    }

    public void updateEvent(Event event) throws EventException {
        User coordinator = requireActiveCoordinator("Only event coordinators can create or edit events.");
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new EventException("Valid event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        Event existing = eventRepository.getEventById(event.getId());
        requireCoordinatorAccess(coordinator.getId(), event.getId(), "You do not have permission to manage this event.");
        normalizeEvent(event);
        validateEvent(event);
        event.setCreatedByUserId(existing.getCreatedByUserId() == null ? coordinator.getId() : existing.getCreatedByUserId());
        if (event.getCoordinatorId() == null || event.getCoordinatorId() <= 0) {
            event.setCoordinatorId(existing.getCoordinatorId() == null ? coordinator.getId() : existing.getCoordinatorId());
        }
        Event updated = eventRepository.updateEvent(event, coordinator.getId());
        event.restoreFrom(updated);
    }

    public void deleteEvent(long eventId) throws EventException {
        setEventDeleted(eventId, true);
    }

    public void setEventDeleted(long eventId, boolean deleted) throws EventException {
        if (eventId <= 0) {
            throw new EventException("Valid event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        User actor = requireActiveAdminOrCoordinator("Only admins or event coordinators can delete events.");
        if (actor.hasRole(Role.COORDINATOR)) {
            requireCoordinatorAccess(actor.getId(), eventId, "You do not have permission to delete this event.");
        }
        eventRepository.setEventDeleted(eventId, actor.getId(), deleted);
    }

    private void normalizeEvent(Event event) {
        if (event == null) {
            return;
        }
        event.setName(EventValidationRules.normalizeRequired(event.getName()));
        event.setLocation(EventValidationRules.normalizeRequired(event.getLocation()));
        event.setLocationGuidance(EventValidationRules.normalizeOptional(event.getLocationGuidance()));
        event.setNotes(EventValidationRules.normalizeRequired(event.getNotes()));
    }

    private void validateEvent(Event event) throws EventException {
        if (event == null) {
            throw new EventException("Event cannot be null.", EventException.ErrorType.VALIDATION_ERROR);
        }

        validateRequiredText("Name", event.getName());
        validateRequiredText("Location", event.getLocation());
        validateRequiredText("Notes", event.getNotes());

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

    private User requireActiveCoordinator(String message) throws EventException {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null
                || currentUser.getId() == null
                || currentUser.getId() <= 0
                || currentUser.isDeleted()
                || !currentUser.hasRole(Role.COORDINATOR)) {
            throw new EventException(message, EventException.ErrorType.VALIDATION_ERROR);
        }
        return currentUser;
    }

    private User requireActiveAdminOrCoordinator(String message) throws EventException {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null
                || currentUser.getId() == null
                || currentUser.getId() <= 0
                || currentUser.isDeleted()
                || (!currentUser.hasRole(Role.ADMIN) && !currentUser.hasRole(Role.COORDINATOR))) {
            throw new EventException(message, EventException.ErrorType.VALIDATION_ERROR);
        }
        return currentUser;
    }

    private void requireCoordinatorAccess(long coordinatorId, long eventId, String message) throws EventException {
        for (Event event : eventRepository.getEventsForCoordinator(coordinatorId)) {
            if (event != null && event.getId() != null && event.getId() == eventId) {
                return;
            }
        }
        throw new EventException(message, EventException.ErrorType.VALIDATION_ERROR);
    }
}
