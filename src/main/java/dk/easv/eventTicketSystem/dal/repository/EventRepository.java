package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.exceptions.EventException;

import java.util.List;

public interface EventRepository {

    List<Event> getAllEvents() throws EventException;

    List<Event> getEventsForCoordinator(long coordinatorId) throws EventException;

    List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) throws EventException;

    List<Event> searchEventsForCoordinator(long coordinatorId, String columnKey, String query, boolean includeDeleted)
            throws EventException;

    Event addEvent(Event event) throws EventException;

    Event updateEvent(Event event) throws EventException;

    void setEventDeleted(long eventId, long coordinatorId, boolean deleted) throws EventException;

    Event getEventById(long eventId) throws EventException;

    int countActiveTicketsForEvent(long eventId) throws EventException;

    int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) throws EventException;
}
