package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.be.Ticket;
import dk.easv.eventTicketSystem.dal.repository.TicketRepository;
import dk.easv.eventTicketSystem.exceptions.TicketException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class TicketDAO implements TicketRepository {

    private static final String COLUMN_CODE = "code";
    private static final String COLUMN_CUSTOMER = "customer";
    private static final String COLUMN_EVENT = "event";
    private static final String COLUMN_STATUS = "status";

    private static final String TICKET_SELECT = """
            SELECT
                t.id,
                t.event_id,
                t.ticket_category_id,
                t.customer_id,
                e.name AS event_name,
                e.location AS event_location,
                e.location_guidance AS event_guidance,
                e.notes AS event_notes,
                e.start_time AS event_start_time,
                e.end_time AS event_end_time,
                t.code,
                c.name AS customer_name,
                c.email AS customer_email,
                t.issued_at,
                t.redeemed_at,
                t.redeemed,
                t.refunded_at
            FROM dbo.Tickets t
            JOIN dbo.Events e ON e.id = t.event_id
            JOIN dbo.Customers c ON c.id = t.customer_id
            """;

    private final Database database;

    public TicketDAO(Database database) {
        this.database = database;
    }

    @Override
    public List<Ticket> getTicketsForEvent(long eventId) throws TicketException {
        return searchTicketsForEvent(eventId, "all", "", true);
    }

    @Override
    public List<Ticket> getAllTickets() throws TicketException {
        return searchAllTickets("all", "", true);
    }

    @Override
    public List<Ticket> searchTicketsForEvent(long eventId,
                                              String columnKey,
                                              String query,
                                              boolean includeRefunded) throws TicketException {
        StringBuilder sql = new StringBuilder(TICKET_SELECT)
                .append(" WHERE t.event_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(eventId);

        if (!includeRefunded) {
            sql.append(" AND t.refunded_at IS NULL");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY t.issued_at DESC, t.id DESC");

        return queryTickets(sql.toString(), params);
    }

    @Override
    public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeRefunded)
            throws TicketException {
        StringBuilder sql = new StringBuilder(TICKET_SELECT)
                .append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (!includeRefunded) {
            sql.append(" AND t.refunded_at IS NULL");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY t.issued_at DESC, t.id DESC");

        return queryTickets(sql.toString(), params);
    }

    @Override
    public Ticket addTicket(long eventId,
                            Long ticketCategoryId,
                            long customerId,
                            String code) throws TicketException {
        if (ticketCategoryId == null || ticketCategoryId <= 0) {
            throw new TicketException("Ticket type is required.", null);
        }
        if (customerId <= 0) {
            throw new TicketException("Customer is required.", null);
        }
        String ticketCode = DaoSupport.isBlank(code) ? Ticket.generateCode() : code.trim();

        try (Connection con = database.getConnection()) {
            ensureActiveEvent(con, eventId);
            int seatCount = ensureActiveTicketCategory(con, eventId, ticketCategoryId);
            ensureCategoryHasAvailableSeats(con, eventId, ticketCategoryId, seatCount);
            ensureCustomerExists(con, customerId);

            try (PreparedStatement stmt = con.prepareStatement("""
                    INSERT INTO dbo.Tickets
                        (event_id, ticket_category_id, customer_id, code, redeemed, refunded_at)
                    VALUES (?, ?, ?, ?, 0, NULL)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, eventId);
                DaoSupport.setLongOrNull(stmt, 2, ticketCategoryId);
                stmt.setLong(3, customerId);
                stmt.setString(4, ticketCode);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return getTicketById(keys.getLong(1));
                    }
                }
            }
        } catch (TicketException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new TicketException("Could not add ticket.", e);
        }

        throw new TicketException("Could not add ticket.", null);
    }

    @Override
    public Ticket getTicketById(long ticketId) throws TicketException {
        List<Ticket> tickets = queryTickets(TICKET_SELECT + " WHERE t.id = ?", List.of(ticketId));
        if (tickets.isEmpty()) {
            throw new TicketException("Ticket not found.", null);
        }
        return tickets.get(0);
    }

    @Override
    public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) throws TicketException {
        try (Connection con = database.getConnection()) {
            return countActiveTicketsForTicketCategory(con, eventId, ticketCategoryId);
        } catch (SQLException | RuntimeException e) {
            throw new TicketException("Could not load ticket usage.", e);
        }
    }

    @Override
    public Ticket refundTicketById(long ticketId) throws TicketException {
        try (Connection con = database.getConnection()) {
            try (PreparedStatement stmt = con.prepareStatement("""
                     UPDATE dbo.Tickets
                     SET refunded_at = COALESCE(refunded_at, SYSDATETIME())
                     WHERE id = ?
                       AND refunded_at IS NULL
                       AND redeemed = 0
                     """)) {
                stmt.setLong(1, ticketId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    throw refundFailureException(ticketId);
                }
                return getTicketById(ticketId);
            }
        } catch (TicketException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new TicketException("Could not update ticket.", e);
        }
    }

    @Override
    public Ticket redeemTicketById(long ticketId) throws TicketException {
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement("""
                     UPDATE dbo.Tickets
                     SET redeemed = 1,
                         redeemed_at = COALESCE(redeemed_at, SYSDATETIME())
                     WHERE id = ?
                       AND refunded_at IS NULL
                       AND redeemed = 0
                     """)) {
            stmt.setLong(1, ticketId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw redeemFailureException(ticketId);
            }
            return getTicketById(ticketId);
        } catch (TicketException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new TicketException("Could not redeem ticket.", e);
        }
    }

    private List<Ticket> queryTickets(String sql, List<Object> params) throws TicketException {
        List<Ticket> tickets = new ArrayList<>();
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tickets.add(DaoSupport.mapTicket(rs));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new TicketException("Could not load tickets.", e);
        }
        return tickets;
    }

    private void ensureActiveEvent(Connection con, long eventId) throws SQLException, TicketException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.Events
                WHERE id = ?
                  AND is_deleted = 0
                """)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new TicketException("Selected event is not available.", null);
                }
            }
        }
    }

    private int ensureActiveTicketCategory(Connection con, long eventId, long ticketCategoryId)
            throws SQLException, TicketException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT seat_count
                FROM dbo.TicketCategories
                WHERE id = ?
                  AND event_id = ?
                  AND is_deleted = 0
                """)) {
            stmt.setLong(1, ticketCategoryId);
            stmt.setLong(2, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new TicketException("Selected ticket type is not available for this event.", null);
                }
                return rs.getInt("seat_count");
            }
        }
    }

    private void ensureCustomerExists(Connection con, long customerId) throws SQLException, TicketException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.Customers
                WHERE id = ?
                """)) {
            stmt.setLong(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new TicketException("Selected customer is not available.", null);
                }
            }
        }
    }

    private void ensureCategoryHasAvailableSeats(Connection con,
                                                 long eventId,
                                                 long ticketCategoryId,
                                                 int seatCount) throws SQLException, TicketException {
        int activeTickets = countActiveTicketsForTicketCategory(con, eventId, ticketCategoryId);
        if (activeTickets >= seatCount) {
            throw new TicketException("No seats are left for the selected ticket type.", null);
        }
    }

    private int countActiveTicketsForTicketCategory(Connection con, long eventId, long ticketCategoryId)
            throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT COUNT(*)
                FROM dbo.Tickets
                WHERE event_id = ?
                  AND ticket_category_id = ?
                  AND refunded_at IS NULL
                """)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, ticketCategoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void appendSearch(StringBuilder sql, List<Object> params, String columnKey, String query) {
        if (DaoSupport.isBlank(query)) {
            return;
        }
        sql.append(" AND ").append(searchExpression(columnKey)).append(" LIKE ?");
        params.add(DaoSupport.likePattern(query));
    }

    private String searchExpression(String columnKey) {
        String normalized = DaoSupport.safe(columnKey);
        return switch (normalized) {
            case COLUMN_CODE -> "LOWER(t.code)";
            case COLUMN_CUSTOMER -> "LOWER(CONCAT(COALESCE(c.name, N''), N' ', COALESCE(c.email, N'')))";
            case COLUMN_EVENT -> "LOWER(e.name)";
            case COLUMN_STATUS -> statusExpression();
            default -> "LOWER(CONCAT(t.code, N' ', COALESCE(c.name, N''), N' ', "
                    + "COALESCE(c.email, N''), N' ', e.name, N' ', "
                    + "CASE WHEN t.refunded_at IS NOT NULL THEN N'Refunded' "
                    + "WHEN t.redeemed = 1 THEN N'Redeemed' ELSE N'Valid' END))";
        };
    }

    private String statusExpression() {
        return "LOWER(CASE WHEN t.refunded_at IS NOT NULL THEN N'Refunded' WHEN t.redeemed = 1 THEN N'Redeemed' ELSE N'Valid' END)";
    }

    private TicketException refundFailureException(long ticketId) throws TicketException {
        Ticket ticket = getTicketById(ticketId);
        if (ticket.isRefunded()) {
            return new TicketException("This ticket is already refunded.", null);
        }
        if (ticket.isRedeemed()) {
            return new TicketException("Redeemed tickets cannot be refunded.", null);
        }
        return new TicketException("Could not refund ticket.", null);
    }

    private TicketException redeemFailureException(long ticketId) throws TicketException {
        Ticket ticket = getTicketById(ticketId);
        if (ticket.isRefunded()) {
            return new TicketException("Refunded tickets cannot be redeemed.", null);
        }
        if (ticket.isRedeemed()) {
            return new TicketException("This ticket is already redeemed.", null);
        }
        return new TicketException("Could not redeem ticket.", null);
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long value) {
                stmt.setLong(i + 1, value);
            } else if (param == null) {
                stmt.setNull(i + 1, Types.NVARCHAR);
            } else {
                stmt.setString(i + 1, String.valueOf(param));
            }
        }
    }
}
