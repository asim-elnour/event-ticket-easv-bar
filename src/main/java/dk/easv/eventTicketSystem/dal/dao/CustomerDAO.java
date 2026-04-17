package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.be.Customer;
import dk.easv.eventTicketSystem.dal.repository.CustomerRepository;
import dk.easv.eventTicketSystem.exceptions.CustomerException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDAO implements CustomerRepository {

    private static final String CUSTOMER_SELECT = """
            SELECT
                c.id,
                c.name,
                c.email,
                c.created_at
            FROM dbo.Customers c
            """;

    private final Database database;

    public CustomerDAO(Database database) {
        this.database = database;
    }

    @Override
    public List<Customer> getCustomersForEvent(long eventId) throws CustomerException {
        StringBuilder sql = new StringBuilder(CUSTOMER_SELECT).append("""
                JOIN dbo.Tickets t ON t.customer_id = c.id
                WHERE t.event_id = ?
                """);
        sql.append(" GROUP BY c.id, c.name, c.email, c.created_at");
        sql.append(" ORDER BY LOWER(c.name), LOWER(c.email), c.id");
        return queryCustomers(sql.toString(), List.of(eventId));
    }

    @Override
    public List<Customer> getAllCustomers() throws CustomerException {
        return queryCustomers(CUSTOMER_SELECT + " ORDER BY LOWER(c.name), LOWER(c.email), c.id", List.of());
    }

    @Override
    public List<Customer> getCustomerDirectory() throws CustomerException {
        return queryCustomers(CUSTOMER_SELECT + " ORDER BY LOWER(c.name), LOWER(c.email), c.id", List.of());
    }

    @Override
    public Customer getCustomerById(long customerId) throws CustomerException {
        List<Customer> customers = queryCustomers(CUSTOMER_SELECT + " WHERE c.id = ?", List.of(customerId));
        if (customers.isEmpty()) {
            throw new CustomerException("Customer not found.");
        }
        return customers.get(0);
    }

    @Override
    public Customer findCustomerByEmail(String email) throws CustomerException {
        List<Customer> customers = queryCustomers(
                CUSTOMER_SELECT + " WHERE LOWER(c.email) = LOWER(?)",
                List.of(email)
        );
        return customers.isEmpty() ? null : customers.get(0);
    }

    @Override
    public Customer createCustomer(String name, String email) throws CustomerException {
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement("""
                     INSERT INTO dbo.Customers (name, email)
                     VALUES (?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return getCustomerById(keys.getLong(1));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new CustomerException("Could not create customer.", e);
        }

        throw new CustomerException("Could not create customer.");
    }

    private List<Customer> queryCustomers(String sql, List<Object> params) throws CustomerException {
        List<Customer> customers = new ArrayList<>();
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    customers.add(DaoSupport.mapCustomer(rs));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new CustomerException("Could not load customers.", e);
        }
        return customers;
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long value) {
                stmt.setLong(i + 1, value);
            } else {
                stmt.setString(i + 1, String.valueOf(param));
            }
        }
    }
}
