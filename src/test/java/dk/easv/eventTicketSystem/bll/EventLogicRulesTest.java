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
    void shouldRejectCapacityBelowOne() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(0, ticketType(1L, "Standard", 1, false));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Capacity must be at least 1.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectTicketSeatsThatDoNotMatchCapacity() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(200, ticketType(1L, "Standard", 100, false));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Ticket type seats must match capacity exactly (allocated 100 / capacity 200).",
                exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectEndBeforeStart() {
        FakeEventRepository repository = new FakeEventRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(100, ticketType(1L, "Standard", 100, false));
        draft.setEndTime(draft.getStartTime().minusHours(1));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("End date and time must be after the start date and time.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectDeletingTicketTypeWithActiveTickets() {
        FakeEventRepository repository = new FakeEventRepository();
        Event stored = storedEvent(10L,
                ticketType(101L, "Standard", 80, false),
                ticketType(102L, "VIP", 20, false));
        repository.store(stored);
        repository.setActiveTicketsForEvent(10L, 20);
        repository.setActiveTicketsForCategory(10L, 101L, 20);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.getTicketTypes().get(0).setDeleted(true);
        edited.getTicketTypes().get(1).setSeatCount(100);

        EventException exception = assertThrows(EventException.class, () -> logic.updateEvent(edited));

        assertEquals("You cannot delete ticket type 'Standard' because it already has 20 active tickets.",
                exception.getMessage());
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldRejectReducingTicketTypeBelowIssuedTickets() {
        FakeEventRepository repository = new FakeEventRepository();
        Event stored = storedEvent(10L,
                ticketType(101L, "Standard", 80, false),
                ticketType(102L, "VIP", 20, false));
        repository.store(stored);
        repository.setActiveTicketsForEvent(10L, 20);
        repository.setActiveTicketsForCategory(10L, 101L, 20);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.getTicketTypes().get(0).setSeatCount(10);
        edited.getTicketTypes().get(1).setSeatCount(90);

        EventException exception = assertThrows(EventException.class, () -> logic.updateEvent(edited));

        assertEquals("You cannot reduce ticket type 'Standard' below 20 active tickets.", exception.getMessage());
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldAllowValidEventUpdate() throws EventException {
        FakeEventRepository repository = new FakeEventRepository();
        Event stored = storedEvent(10L,
                ticketType(101L, "Standard", 80, false),
                ticketType(102L, "VIP", 20, false));
        repository.store(stored);
        repository.setActiveTicketsForEvent(10L, 20);
        repository.setActiveTicketsForCategory(10L, 101L, 20);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.getTicketTypes().get(0).setSeatCount(60);
        edited.getTicketTypes().get(1).setSeatCount(40);

        logic.updateEvent(edited);

        assertTrue(repository.updateCalled);
        assertEquals(60, edited.getTicketTypes().get(0).getSeatCount());
        assertEquals(40, edited.getTicketTypes().get(1).getSeatCount());
    }

    private static Event eventDraft(int capacity, TicketCategory... categories) {
        Event event = new Event();
        event.setName("Friday Bar");
        event.setLocation("EASV");
        event.setStartTime(LocalDateTime.of(2026, 5, 1, 20, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 2, 1, 0));
        event.setCapacity(capacity);
        event.setTicketTypes(List.of(categories));
        return event;
    }

    private static Event storedEvent(long eventId, TicketCategory... categories) {
        Event event = eventDraft(100, categories);
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
        private final Map<Long, Integer> activeTicketsByEvent = new HashMap<>();
        private final Map<String, Integer> activeTicketsByCategory = new HashMap<>();

        private boolean addCalled;
        private boolean updateCalled;
        private long nextId = 100L;

        void store(Event event) {
            events.put(event.getId(), event.copy());
        }

        void setActiveTicketsForEvent(long eventId, int count) {
            activeTicketsByEvent.put(eventId, count);
        }

        void setActiveTicketsForCategory(long eventId, long categoryId, int count) {
            activeTicketsByCategory.put(categoryKey(eventId, categoryId), count);
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
            return activeTicketsByEvent.getOrDefault(eventId, 0);
        }

        @Override
        public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) {
            return activeTicketsByCategory.getOrDefault(categoryKey(eventId, ticketCategoryId), 0);
        }

        private List<Event> copyAll() {
            List<Event> copies = new ArrayList<>();
            for (Event event : events.values()) {
                copies.add(event.copy());
            }
            return copies;
        }

        private String categoryKey(long eventId, long categoryId) {
            return eventId + ":" + categoryId;
        }
    }
}
