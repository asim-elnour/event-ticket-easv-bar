package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.dal.repository.CustomerRepository;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.exceptions.CustomerException;
import dk.easv.eventTicketSystem.util.CustomerValidationRules;

import java.util.List;

public class CustomerLogic {

    private final CustomerRepository customerRepository;

    public CustomerLogic() {
        this(RepositoryProvider.customers());
    }

    public CustomerLogic(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getCustomersForEvent(long eventId) throws CustomerException {
        if (eventId <= 0) {
            return List.of();
        }
        return customerRepository.getCustomersForEvent(eventId);
    }

    public List<Customer> getAllCustomers() throws CustomerException {
        return customerRepository.getAllCustomers();
    }

    public List<Customer> getCustomerDirectory() throws CustomerException {
        return customerRepository.getCustomerDirectory();
    }

    public Customer getCustomerById(long customerId) throws CustomerException {
        if (customerId <= 0) {
            throw new CustomerException("Customer not found.");
        }
        return customerRepository.getCustomerById(customerId);
    }

    public Customer resolveOrCreateCustomer(String name, String email) throws CustomerException {
        String normalizedName = normalizeRequired(name, "Customer name");
        String normalizedEmail = normalizeEmail(email);

        Customer existing = customerRepository.findCustomerByEmail(normalizedEmail);
        if (existing == null) {
            return customerRepository.createCustomer(normalizedName, normalizedEmail);
        }
        if (!CustomerValidationRules.namesMatch(existing.getName(), normalizedName)) {
            throw new CustomerException(
                    "This email already belongs to an existing customer. Please select Existing Customer."
            );
        }
        return existing;
    }

    private String normalizeRequired(String value, String fieldName) throws CustomerException {
        String normalized = CustomerValidationRules.normalizeRequired(value);
        if (normalized.isEmpty()) {
            throw new CustomerException(fieldName + " is required.");
        }
        if (normalized.length() > CustomerValidationRules.MAX_TEXT_LENGTH) {
            throw new CustomerException(fieldName + " is too long.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) throws CustomerException {
        String normalized = normalizeRequired(email, "Customer email");
        if (!CustomerValidationRules.isValidEmail(normalized)) {
            throw new CustomerException("Customer email must be a valid email address.");
        }
        return normalized;
    }
}
