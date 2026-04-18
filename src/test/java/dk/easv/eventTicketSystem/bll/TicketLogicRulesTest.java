package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.CustomerRepository;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.exceptions.TicketException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 5, false));
        event.setDeleted(true);
        eventRepository.store(event);

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "Alice", "alice@example.com", "CODE-1"));

        assertEquals("Cannot issue tickets for a deleted event.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectAddingTicketForSoldOutCategory() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        Event event = eventWithCategory(10L, false, category(100L, "VIP", 2, false));
        eventRepository.store(event);
        ticketRepository.setActiveTicketsForCategory(10L, 100L, 2);

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "Alice", "alice@example.com", "CODE-1"));

        assertEquals("No seats are left for ticket type 'VIP'.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectAddingTicketWithoutCustomerName() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "   ", "alice@example.com", "CODE-0"));

        assertEquals("Customer name is required.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectAddingTicketWithoutTicketType() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, null, "Alice", "alice@example.com", "CODE-0"));

        assertEquals("Ticket type is required.", exception.getMessage());
        assertFalse(ticketRepository.addCalled);
    }

    @Test
    void shouldRejectRefundingRedeemedTicket() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(redeemedTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.refundTicketById(1L));

        assertEquals("Redeemed tickets cannot be refunded.", exception.getMessage());
        assertFalse(ticketRepository.refundCalled);
    }

    @Test
    void shouldRejectRedeemingRefundedTicket() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(refundedTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.redeemTicketById(1L));

        assertEquals("Refunded tickets cannot be redeemed.", exception.getMessage());
        assertFalse(ticketRepository.redeemCalled);
    }

    @Test
    void shouldRefundValidTicket() throws TicketException {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(validTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        Ticket refunded = logic.refundTicketById(1L);

        assertTrue(ticketRepository.refundCalled);
        assertTrue(refunded.isRefunded());
    }

    @Test
    void shouldRejectRefundWithinThirtyMinutesOfEventStart() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(validTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(
                ticketRepository,
                eventRepository,
                new CustomerLogic(customerRepository),
                fixedClock(LocalDateTime.of(2026, 5, 1, 19, 35))
        );

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.refundTicketById(1L));

        assertEquals("Tickets can only be refunded more than 30 minutes before the event starts.", exception.getMessage());
        assertFalse(ticketRepository.refundCalled);
    }

    @Test
    void shouldRejectRedeemEarlierThanThirtyMinutesBeforeStart() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(validTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(
                ticketRepository,
                eventRepository,
                new CustomerLogic(customerRepository),
                fixedClock(LocalDateTime.of(2026, 5, 1, 19, 20))
        );

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.redeemTicketById(1L));

        assertEquals("Tickets can only be redeemed from 30 minutes before the event starts.", exception.getMessage());
        assertFalse(ticketRepository.redeemCalled);
    }

    @Test
    void shouldRedeemTicketInsideAllowedWindow() throws TicketException {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(validTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(
                ticketRepository,
                eventRepository,
                new CustomerLogic(customerRepository),
                fixedClock(LocalDateTime.of(2026, 5, 1, 19, 30))
        );

        Ticket redeemed = logic.redeemTicketById(1L);

        assertTrue(ticketRepository.redeemCalled);
        assertTrue(redeemed.isRedeemed());
    }

    @Test
    void shouldRejectRedeemAfterEventEnd() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        eventRepository.store(eventWithCategory(10L, false, category(100L, "Standard", 5, false)));
        ticketRepository.store(validTicket(1L, 10L, 100L));

        TicketLogic logic = new TicketLogic(
                ticketRepository,
                eventRepository,
                new CustomerLogic(customerRepository),
                fixedClock(LocalDateTime.of(2026, 5, 2, 1, 5))
        );

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.redeemTicketById(1L));

        assertEquals("This event has already ended. Tickets can no longer be redeemed.", exception.getMessage());
        assertFalse(ticketRepository.redeemCalled);
    }

    @Test
    void shouldReuseExistingCustomerWhenNameAndEmailMatch() throws TicketException {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 10, false));
        eventRepository.store(event);
        customerRepository.store(customer(7L, "Alice Student", "alice@example.com"));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        logic.addTicket(10L, 100L, "Alice Student", "alice@example.com", "CODE-7");

        assertTrue(ticketRepository.addCalled);
        assertEquals(7L, ticketRepository.lastCustomerId);
        assertFalse(customerRepository.createCalled);
    }

    @Test
    void shouldRejectAddingTicketWhenEmailBelongsToDifferentCustomerName() {
        FakeEventRepository eventRepository = new FakeEventRepository();
        FakeTicketRepository ticketRepository = new FakeTicketRepository();
        FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        Event event = eventWithCategory(10L, false, category(100L, "Standard", 10, false));
        eventRepository.store(event);
        customerRepository.store(customer(7L, "Alice Student", "alice@example.com"));

        TicketLogic logic = new TicketLogic(ticketRepository, eventRepository, new CustomerLogic(customerRepository));

        TicketException exception = assertThrows(TicketException.class,
                () -> logic.addTicket(10L, 100L, "Alice Changed", "alice@example.com", "CODE-8"));

        assertEquals(
                "This email already belongs to an existing customer. Search and select that customer above.",
                exception.getMessage()
        );
        assertFalse(ticketRepository.addCalled);
    }

    private static Event eventWithCategory(long eventId, boolean deleted, TicketCategory category) {
        Event event = new Event();
        event.setId(eventId);
        event.setName("Friday Bar");
        event.setLocation("EASV");
        event.setStartTime(LocalDateTime.of(2026, 5, 1, 20, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 2, 1, 0));
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

    private static Ticket validTicket(long ticketId, long eventId, long categoryId) {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setEventId(eventId);
        ticket.setTicketCategoryId(categoryId);
        ticket.setCode("CODE-" + ticketId);
        return ticket;
    }

    private static Ticket refundedTicket(long ticketId, long eventId, long categoryId) {
        Ticket ticket = validTicket(ticketId, eventId, categoryId);
        ticket.setRefundedAt(LocalDateTime.of(2026, 5, 1, 12, 0));
        return ticket;
    }

    private static Ticket redeemedTicket(long ticketId, long eventId, long categoryId) {
        Ticket ticket = validTicket(ticketId, eventId, categoryId);
        ticket.setRedeemedAt(LocalDateTime.of(2026, 5, 1, 12, 0));
        return ticket;
    }

    private static Customer customer(long customerId, String name, String email) {
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setName(name);
        customer.setEmail(email);
        customer.setCreatedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        return customer;
    }

    private static Clock fixedClock(LocalDateTime value) {
        return Clock.fixed(value.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
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
        private boolean refundCalled;
        private boolean redeemCalled;
        private long lastCustomerId;
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
        public List<Ticket> searchTicketsForEvent(long eventId, String columnKey, String query, boolean includeRefunded) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeRefunded) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ticket addTicket(long eventId, Long ticketCategoryId, long customerId, String code) {
            addCalled = true;
            lastCustomerId = customerId;
            Ticket ticket = new Ticket();
            ticket.setId(nextId++);
            ticket.setEventId(eventId);
            ticket.setTicketCategoryId(ticketCategoryId);
            ticket.setCustomerId(customerId);
            ticket.setCode(code);
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
        public Ticket refundTicketById(long ticketId) throws TicketException {
            refundCalled = true;
            Ticket ticket = tickets.get(ticketId);
            if (ticket == null) {
                throw new TicketException("Ticket not found.", null);
            }
            ticket.setRefundedAt(LocalDateTime.of(2026, 5, 1, 12, 30));
            tickets.put(ticketId, ticket.copy());
            return ticket.copy();
        }

        @Override
        public Ticket redeemTicketById(long ticketId) throws TicketException {
            redeemCalled = true;
            Ticket ticket = tickets.get(ticketId);
            if (ticket == null) {
                throw new TicketException("Ticket not found.", null);
            }
            ticket.setRedeemedAt(LocalDateTime.of(2026, 5, 1, 13, 0));
            tickets.put(ticketId, ticket.copy());
            return ticket.copy();
        }

        private String categoryKey(long eventId, long categoryId) {
            return eventId + ":" + categoryId;
        }
    }

    private static final class FakeCustomerRepository implements CustomerRepository {
        private final Map<Long, Customer> customers = new HashMap<>();
        private long nextId = 100L;
        private boolean createCalled;

        void store(Customer customer) {
            customers.put(customer.getId(), customer.copy());
        }

        @Override
        public List<Customer> getCustomersForEvent(long eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Customer> getAllCustomers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Customer> getCustomerDirectory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Customer getCustomerById(long customerId) throws CustomerException {
            Customer customer = customers.get(customerId);
            if (customer == null) {
                throw new CustomerException("Customer not found.");
            }
            return customer.copy();
        }

        @Override
        public Customer findCustomerByEmail(String email) {
            String normalized = email == null ? "" : email.trim().toLowerCase();
            return customers.values().stream()
                    .filter(customer -> customer.getEmail() != null
                            && customer.getEmail().trim().toLowerCase().equals(normalized))
                    .findFirst()
                    .map(Customer::copy)
                    .orElse(null);
        }

        @Override
        public Customer createCustomer(String name, String email) {
            createCalled = true;
            Customer customer = customer(nextId++, name, email);
            customers.put(customer.getId(), customer.copy());
            return customer.copy();
        }
    }
}
