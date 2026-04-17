package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.exceptions.EventException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogicRulesTest {

    @Test
    void shouldRejectEventWithoutActiveTicketType() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 10, true));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("At least one active ticket type is required.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectTicketTypeSeatsBelowOne() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 0, false));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Ticket seats must be at least 1.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectEndBeforeStart() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));
        draft.setEndTime(draft.getStartTime().minusHours(1));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("End date and time must be after the start date and time.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldAllowReducingTypeBelowSoldBecauseRepositoryHandlesRefundFlow() throws EventException {
        FakeEventRepository repository = new FakeEventRepository();
        Event stored = storedEvent(10L,
                ticketType(101L, "Standard", 80, false),
                ticketType(102L, "VIP", 20, false));
        repository.store(stored);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.getTicketTypes().get(0).setSeatCount(10);

        logic.updateEvent(edited);

        assertTrue(repository.updateCalled);
        assertEquals(10, edited.getTicketTypes().get(0).getSeatCount());
    }

    @Test
    void shouldAllowDeletingSoldTicketTypeBecauseRepositoryHandlesRefundFlow() throws EventException {
        FakeEventRepository repository = new FakeEventRepository();
        Event stored = storedEvent(10L,
                ticketType(101L, "Standard", 80, false),
                ticketType(102L, "VIP", 20, false));
        repository.store(stored);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.getTicketTypes().get(0).setDeleted(true);

        logic.updateEvent(edited);

        assertTrue(repository.updateCalled);
        assertTrue(edited.getTicketTypes().get(0).isDeleted());
    }

    private static Event eventDraft(TicketCategory... categories) {
        Event event = new Event();
        event.setName("Friday Bar");
        event.setLocation("EASV");
        event.setStartTime(LocalDateTime.of(2026, 5, 1, 20, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 2, 1, 0));
        event.setTicketTypes(List.of(categories));
        return event;
    }

    private static Event storedEvent(long eventId, TicketCategory... categories) {
        Event event = eventDraft(categories);
        event.setId(eventId);
        return event;
    }

    private static TicketCategory ticketType(Long id, String name, int seats, boolean deleted) {
        TicketCategory category = new TicketCategory();
        category.setId(id);
        category.setName(name);
        category.setPrice(new BigDecimal("60.00"));
        category.setSeatCount(seats);
        category.setDeleted(deleted);
        return category;
    }

    private static final class FakeEventRepository implements EventRepository {
        private final Map<Long, Event> events = new HashMap<>();

        private boolean addCalled;
        private boolean updateCalled;
        private long nextId = 100L;

        void store(Event event) {
            events.put(event.getId(), event.copy());
        }

        @Override
        public List<Event> getAllEvents() {
            return copyAll();
        }

        @Override
        public List<Event> getEventsForCoordinator(long coordinatorId) {
            return copyAll();
        }

        @Override
        public List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) {
            return copyAll();
        }

        @Override
        public List<Event> searchEventsForCoordinator(long coordinatorId, String columnKey, String query, boolean includeDeleted) {
            return copyAll();
        }

        @Override
        public Event addEvent(Event event) {
            addCalled = true;
            Event stored = event.copy();
            if (stored.getId() == null || stored.getId() <= 0) {
                stored.setId(nextId++);
            }
            events.put(stored.getId(), stored.copy());
            return stored.copy();
        }

        @Override
        public Event updateEvent(Event event) {
            updateCalled = true;
            events.put(event.getId(), event.copy());
            return event.copy();
        }

        @Override
        public void setEventDeleted(long eventId, long coordinatorId, boolean deleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event getEventById(long eventId) throws EventException {
            Event event = events.get(eventId);
            if (event == null) {
                throw new EventException("Event not found.", EventException.ErrorType.NOT_FOUND);
            }
            return event.copy();
        }

        @Override
        public int countActiveTicketsForEvent(long eventId) {
            return 0;
        }

        @Override
        public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) {
            return 0;
        }

        private List<Event> copyAll() {
            List<Event> copies = new ArrayList<>();
            for (Event event : events.values()) {
                copies.add(event.copy());
            }
            return copies;
        }
    }
}
