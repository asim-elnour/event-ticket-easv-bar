package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void clearSession() {
        SessionManager.clearCurrentUser();
    }

    @Test
    void shouldRejectEventWithoutActiveTicketType() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 10, true));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("At least one active ticket type is required.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectTicketTypeSeatsBelowOne() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 0, false));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Ticket seats must be at least 1.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectEndBeforeStart() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));
        draft.setEndTime(draft.getStartTime().minusHours(1));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("End date and time must be after the start date and time.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectMissingStartDateTime() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));
        draft.setStartTime(null);

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Start date and time are required.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectNegativeTicketPrice() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));
        draft.getTicketTypes().get(0).setPrice(new BigDecimal("-1.00"));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Ticket price must be zero or higher.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectMissingNotes() {
        FakeEventRepository repository = coordinatorRepository(2L);
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));
        draft.setNotes("   ");

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Notes is required.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectCreateWhenCurrentUserIsAdmin() {
        FakeEventRepository repository = adminRepository();
        EventLogic logic = new EventLogic(repository);
        Event draft = eventDraft(ticketType(1L, "Standard", 100, false));

        EventException exception = assertThrows(EventException.class, () -> logic.addEvent(draft));

        assertEquals("Only event coordinators can create or edit events.", exception.getMessage());
        assertFalse(repository.addCalled);
    }

    @Test
    void shouldRejectUpdatingEventWithoutCoordinatorAccess() {
        FakeEventRepository repository = coordinatorRepository(2L);
        Event stored = storedEvent(10L, 99L, ticketType(101L, "Standard", 80, false));
        repository.store(stored);

        EventLogic logic = new EventLogic(repository);
        Event edited = stored.copy();
        edited.setName("Blocked Edit");

        EventException exception = assertThrows(EventException.class, () -> logic.updateEvent(edited));

        assertEquals("You do not have permission to manage this event.", exception.getMessage());
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldAllowReducingTypeBelowSoldBecauseRepositoryHandlesRefundFlow() throws EventException {
        FakeEventRepository repository = coordinatorRepository(2L);
        Event stored = storedEvent(10L, 2L,
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
        FakeEventRepository repository = coordinatorRepository(2L);
        Event stored = storedEvent(10L, 2L,
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

    @Test
    void shouldAllowDeletingEventWhenCurrentUserIsAdmin() throws EventException {
        FakeEventRepository repository = adminRepository();
        Event stored = storedEvent(10L, 2L, ticketType(101L, "Standard", 80, false));
        repository.store(stored);

        EventLogic logic = new EventLogic(repository);

        logic.setEventDeleted(10L, true);

        assertTrue(repository.deleteCalled);
        assertTrue(repository.getEventById(10L).isDeleted());
    }

    private static Event eventDraft(TicketCategory... categories) {
        Event event = new Event();
        event.setName("Friday Bar");
        event.setLocation("EASV");
        event.setNotes("Bring student ID.");
        event.setStartTime(LocalDateTime.of(2026, 5, 1, 20, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 2, 1, 0));
        event.setTicketTypes(List.of(categories));
        return event;
    }

    private static Event storedEvent(long eventId, long coordinatorId, TicketCategory... categories) {
        Event event = eventDraft(categories);
        event.setId(eventId);
        event.setCoordinatorId(coordinatorId);
        event.setCreatedByUserId(coordinatorId);
        return event;
    }

    private static FakeEventRepository coordinatorRepository(long coordinatorId) {
        FakeEventRepository repository = new FakeEventRepository();
        SessionManager.setCurrentUser(storedUser(coordinatorId, "coord-" + coordinatorId, Role.COORDINATOR));
        return repository;
    }

    private static FakeEventRepository adminRepository() {
        FakeEventRepository repository = new FakeEventRepository();
        SessionManager.setCurrentUser(storedUser(1L, "admin", Role.ADMIN));
        return repository;
    }

    private static User storedUser(long id, String username, Role role) {
        User user = new User(username, "Stored", "User", username + "@example.com", "password123", role);
        user.idProperty().set(id);
        user.setDeleted(false);
        return user;
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
        private boolean deleteCalled;
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
            return coordinatorEvents(coordinatorId);
        }

        @Override
        public List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) {
            return copyAll();
        }

        @Override
        public List<Event> searchEventsForCoordinator(long coordinatorId, String columnKey, String query, boolean includeDeleted) {
            return coordinatorEvents(coordinatorId);
        }

        @Override
        public Event addEvent(Event event, long actorUserId) {
            addCalled = true;
            Event stored = event.copy();
            if (stored.getId() == null || stored.getId() <= 0) {
                stored.setId(nextId++);
            }
            events.put(stored.getId(), stored.copy());
            return stored.copy();
        }

        @Override
        public Event updateEvent(Event event, long actorUserId) {
            updateCalled = true;
            events.put(event.getId(), event.copy());
            return event.copy();
        }

        @Override
        public void setEventDeleted(long eventId, long actorUserId, boolean deleted) throws EventException {
            deleteCalled = true;
            Event event = events.get(eventId);
            if (event == null) {
                throw new EventException("Event not found.", EventException.ErrorType.NOT_FOUND);
            }
            event.setDeleted(deleted);
            events.put(eventId, event.copy());
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

        private List<Event> coordinatorEvents(long coordinatorId) {
            List<Event> copies = new ArrayList<>();
            for (Event event : events.values()) {
                if (event == null) {
                    continue;
                }
                boolean accessible = event.getCoordinatorId() != null && event.getCoordinatorId().equals(coordinatorId);
                if (!accessible && event.getCreatedByUserId() != null) {
                    accessible = event.getCreatedByUserId().equals(coordinatorId);
                }
                if (accessible) {
                    copies.add(event.copy());
                }
            }
            return copies;
        }
    }
}
