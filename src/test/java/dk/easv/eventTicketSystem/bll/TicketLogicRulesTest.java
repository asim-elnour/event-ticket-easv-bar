package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketLogicRulesTest {

    @Test
    void shouldRejectAddingTicketForDeletedEvent() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 5, false));
        event.setDeleted(true);
        eventRepository.store(event);

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository);

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "Alice", "alice@example.com", "CODE-1"));

        assertEquals("Cannot issue tickets for a deleted event.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectAddingTicketForSoldOutCategory() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        Event event = eventWithCategory(10L, false, category(100L, "VIP", 2, false));
        eventRepository.store(event);
        ticketRepository.setActiveTicketsForCategory(10L, 100L, 2);

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository);

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "Alice", "alice@example.com", "CODE-1"));

        assertEquals("No seats are left for ticket type 'VIP'.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectRestoringTicketWhenEventIsDeleted() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 5, false));
        event.setDeleted(true);
        eventRepository.store(event);
        ticketRepository.store(ticket(1L, 10L, 100L, true));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository);

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.setTicketDeletedState(1L, false));

        assertEquals("Cannot restore a ticket for a deleted event.", exception.getMessage());
        assertFalse(ticketRepository.stateChangeCalled);
    }

    @Test
    void shouldRejectRestoringTicketWhenCategoryIsDeleted() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 5, true));
        eventRepository.store(event);
        ticketRepository.store(ticket(1L, 10L, 100L, true));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository);

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.setTicketDeletedState(1L, false));

        assertEquals("Cannot restore this ticket because its ticket type is deleted.", exception.getMessage());
        assertFalse(ticketRepository.stateChangeCalled);
    }

    @Test
    void shouldRestoreDeletedTicketWhenSeatsAreAvailable() throws TicketException {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 5, false));
        eventRepository.store(event);
        ticketRepository.store(ticket(1L, 10L, 100L, true));
        ticketRepository.setActiveTicketsForCategory(10L, 100L, 4);

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository);

        Ticket restored = logic.setTicketDeletedState(1L, false);

        assertTrue(ticketRepository.stateChangeCalled);
        assertFalse(restored.isDeleted());
    }

    private static Event eventWithCategory(long eventId, boolean deleted, TicketCategory category) {
        Event event = new Event();
        event.setId(eventId);
        event.setName("Friday Bar");
        event.setLocation("EASV");
        event.setStartTime(LocalDateTime.of(2026, 5, 1, 20, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 2, 1, 0));
        event.setCapacity(category.getSeatCount());
        event.setDeleted(deleted);
        event.setTicketTypes(List.of(category));
        return event;
    }

    private static TicketCategory category(Long categoryId, String name, int seats, boolean deleted) {
        TicketCategory category = new TicketCategory();
        category.setId(categoryId);
        category.setName(name);
        category.setPrice(new BigDecimal("60.00"));
        category.setSeatCount(seats);
        category.setDeleted(deleted);
        return category;
    }

    private static Ticket ticket(long ticketId, long eventId, long categoryId, boolean deleted) {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setEventId(eventId);
        ticket.setTicketCategoryId(categoryId);
        ticket.setDeleted(deleted);
        ticket.setCode("CODE-" + ticketId);
        return ticket;
    }

    private static final class FakeEventRepository implements EventRepository {
        private final Map<Long, Event> events = new HashMap<>();

        void store(Event event) {
            events.put(event.getId(), event.copy());
        }

        @Override
        public List<Event> getAllEvents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Event> getEventsForCoordinator(long coordinatorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Event> searchEventsForCoordinator(long coordinatorId, String columnKey, String query, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event addEvent(Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event updateEvent(Event event) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeTicketRepository implements TicketRepository {
        private final Map<Long, Ticket> tickets = new HashMap<>();
        private final Map<String, Integer> activeTicketsByCategory = new HashMap<>();

        private boolean addCalled;
        private boolean stateChangeCalled;
        private long nextId = 100L;

        void store(Ticket ticket) {
            tickets.put(ticket.getId(), ticket.copy());
        }

        void setActiveTicketsForCategory(long eventId, long categoryId, int count) {
            activeTicketsByCategory.put(categoryKey(eventId, categoryId), count);
        }

        @Override
        public List<Ticket> getTicketsForEvent(long eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Ticket> getAllTickets() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Ticket> searchTicketsForEvent(long eventId, String columnKey, String query, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ticket addTicket(long eventId, Long ticketCategoryId, String customerName, String customerEmail, String code) {
            addCalled = true;
            Ticket ticket = new Ticket();
            ticket.setId(nextId++);
            ticket.setEventId(eventId);
            ticket.setTicketCategoryId(ticketCategoryId);
            ticket.setCustomerName(customerName);
            ticket.setCustomerEmail(customerEmail);
            ticket.setCode(code);
            ticket.setDeleted(false);
            tickets.put(ticket.getId(), ticket.copy());
            return ticket.copy();
        }

        @Override
        public Ticket getTicketById(long ticketId) throws TicketException {
            Ticket ticket = tickets.get(ticketId);
            if (ticket == null) {
                throw new TicketException("Ticket not found.", null);
            }
            return ticket.copy();
        }

        @Override
        public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) {
            return activeTicketsByCategory.getOrDefault(categoryKey(eventId, ticketCategoryId), 0);
        }

        @Override
        public Ticket setTicketDeletedState(long ticketId, boolean deleted) throws TicketException {
            stateChangeCalled = true;
            Ticket ticket = tickets.get(ticketId);
            if (ticket == null) {
                throw new TicketException("Ticket not found.", null);
            }
            ticket.setDeleted(deleted);
            tickets.put(ticketId, ticket.copy());
            return ticket.copy();
        }

        @Override
        public Ticket redeemTicketById(long ticketId) {
            throw new UnsupportedOperationException();
        }

        private String categoryKey(long eventId, long categoryId) {
            return eventId + ":" + categoryId;
        }
    }
}
