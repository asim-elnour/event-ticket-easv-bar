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
                e.name AS event_name,
                t.code,
                t.customer_name,
                t.customer_email,
                t.issued_at,
                t.redeemed_at,
                t.redeemed,
                t.deleted
            FROM dbo.Tickets t
            JOIN dbo.Events e ON e.id = t.event_id
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
                                              boolean includeDeleted) throws TicketException {
        StringBuilder sql = new StringBuilder(TICKET_SELECT)
                .append(" WHERE t.event_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(eventId);

        if (!includeDeleted) {
            sql.append(" AND t.deleted = 0");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY t.issued_at DESC, t.id DESC");

        return queryTickets(sql.toString(), params);
    }

    @Override
    public List<Ticket> searchAllTickets(String columnKey, String query, boolean includeDeleted)
            throws TicketException {
        StringBuilder sql = new StringBuilder(TICKET_SELECT)
                .append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append(" AND t.deleted = 0");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY t.issued_at DESC, t.id DESC");

        return queryTickets(sql.toString(), params);
    }

    @Override
    public Ticket addTicket(long eventId,
                            Long ticketCategoryId,
                            String customerName,
                            String customerEmail,
                            String code) throws TicketException {
        if (ticketCategoryId == null || ticketCategoryId <= 0) {
            throw new TicketException("Ticket type is required.", null);
        }
        String ticketCode = DaoSupport.isBlank(code) ? Ticket.generateCode() : code.trim();

        try (Connection con = database.getConnection()) {
            ensureActiveEvent(con, eventId);
            int seatCount = ensureActiveTicketCategory(con, eventId, ticketCategoryId);
            ensureCategoryHasAvailableSeats(con, eventId, ticketCategoryId, seatCount);

            try (PreparedStatement stmt = con.prepareStatement("""
                    INSERT INTO dbo.Tickets
                        (event_id, ticket_category_id, code, customer_name, customer_email, redeemed, deleted)
                    VALUES (?, ?, ?, ?, ?, 0, 0)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, eventId);
                DaoSupport.setLongOrNull(stmt, 2, ticketCategoryId);
                stmt.setString(3, ticketCode);
                stmt.setString(4, customerName);
                stmt.setString(5, customerEmail);
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
    public Ticket setTicketDeletedState(long ticketId, boolean deleted) throws TicketException {
        try (Connection con = database.getConnection()) {
            if (!deleted) {
                ensureTicketCanBeRestored(con, ticketId);
            }

            try (PreparedStatement stmt = con.prepareStatement("""
                     UPDATE dbo.Tickets
                     SET deleted = ?
                     WHERE id = ?
                     """)) {
                stmt.setBoolean(1, deleted);
                stmt.setLong(2, ticketId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    throw new TicketException("Ticket not found.", null);
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
                     """)) {
            stmt.setLong(1, ticketId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new TicketException("Ticket not found.", null);
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
                  AND deleted = 0
                """)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, ticketCategoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void ensureTicketCanBeRestored(Connection con, long ticketId) throws SQLException, TicketException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT
                    t.event_id,
                    t.ticket_category_id,
                    t.deleted,
                    e.is_deleted AS event_deleted,
                    tc.id AS category_id_check,
                    tc.is_deleted AS category_deleted,
                    tc.seat_count
                FROM dbo.Tickets t
                JOIN dbo.Events e ON e.id = t.event_id
                LEFT JOIN dbo.TicketCategories tc
                    ON tc.id = t.ticket_category_id
                   AND tc.event_id = t.event_id
                WHERE t.id = ?
                """)) {
            stmt.setLong(1, ticketId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new TicketException("Ticket not found.", null);
                }
                if (!rs.getBoolean("deleted")) {
                    return;
                }
                if (rs.getBoolean("event_deleted")) {
                    throw new TicketException("Cannot restore a ticket for a deleted event.", null);
                }

                Long ticketCategoryId = DaoSupport.getLongObject(rs, "ticket_category_id");
                if (ticketCategoryId == null || ticketCategoryId <= 0) {
                    throw new TicketException("Cannot restore a ticket without a ticket type.", null);
                }

                Long existingCategoryId = DaoSupport.getLongObject(rs, "category_id_check");
                if (existingCategoryId == null || rs.getBoolean("category_deleted")) {
                    throw new TicketException("Cannot restore this ticket because its ticket type is deleted.", null);
                }

                long eventId = rs.getLong("event_id");
                int seatCount = rs.getInt("seat_count");
                ensureCategoryHasAvailableSeats(con, eventId, ticketCategoryId, seatCount);
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
            case COLUMN_CUSTOMER -> "LOWER(CONCAT(COALESCE(t.customer_name, N''), N' ', COALESCE(t.customer_email, N'')))";
            case COLUMN_EVENT -> "LOWER(e.name)";
            case COLUMN_STATUS -> statusExpression();
            default -> "LOWER(CONCAT(t.code, N' ', COALESCE(t.customer_name, N''), N' ', "
                    + "COALESCE(t.customer_email, N''), N' ', e.name, N' ', "
                    + "CASE WHEN t.deleted = 1 THEN N'Deleted' "
                    + "WHEN t.redeemed = 1 THEN N'Redeemed' ELSE N'Valid' END))";
        };
    }

    private String statusExpression() {
        return "LOWER(CASE WHEN t.deleted = 1 THEN N'Deleted' WHEN t.redeemed = 1 THEN N'Redeemed' ELSE N'Valid' END)";
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
