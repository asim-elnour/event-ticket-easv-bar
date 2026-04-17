package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.exceptions.CustomerException;

import java.util.List;

public interface CustomerRepository {

    List<Customer> getCustomersForEvent(long eventId) throws CustomerException;

    List<Customer> getAllCustomers() throws CustomerException;

    List<Customer> getCustomerDirectory() throws CustomerException;

    Customer getCustomerById(long customerId) throws CustomerException;

    Customer findCustomerByEmail(String email) throws CustomerException;

    Customer createCustomer(String name, String email) throws CustomerException;
}
