package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.dal.repository.CustomerRepository;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerLogicRulesTest {

    @Test
    void shouldCreateCustomerWhenEmailIsNew() throws CustomerException {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        CustomerLogic logic = new CustomerLogic(repository);

        Customer customer = logic.resolveOrCreateCustomer("Alice Student", "alice@example.com");

        assertTrue(repository.createCalled);
        assertEquals("Alice Student", customer.getName());
        assertEquals("alice@example.com", customer.getEmail());
    }

    @Test
    void shouldReuseCustomerWhenEmailAndNameMatch() throws CustomerException {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        repository.store(customer(5L, "Alice Student", "alice@example.com"));
        CustomerLogic logic = new CustomerLogic(repository);

        Customer customer = logic.resolveOrCreateCustomer("Alice Student", "alice@example.com");

        assertEquals(5L, customer.getId());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectCustomerWhenEmailMatchesButNameDiffers() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        repository.store(customer(5L, "Alice Student", "alice@example.com"));
        CustomerLogic logic = new CustomerLogic(repository);

        CustomerException exception = assertThrows(CustomerException.class,
                () -> logic.resolveOrCreateCustomer("Alice Changed", "alice@example.com"));

        assertEquals(
                "This email already belongs to an existing customer. Search and select that customer above.",
                exception.getMessage()
        );
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectInvalidCustomerEmail() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        CustomerLogic logic = new CustomerLogic(repository);

        CustomerException exception = assertThrows(CustomerException.class,
                () -> logic.resolveOrCreateCustomer("Alice Student", "bad-email"));

        assertEquals("Customer email must be a valid email address.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    private static Customer customer(long id, String name, String email) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName(name);
        customer.setEmail(email);
        customer.setCreatedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        return customer;
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
