package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.TicketCategory;
import dk.easv.eventTicketSystem.dal.repository.EventRepository;
import dk.easv.eventTicketSystem.exceptions.EventException;
import dk.easv.eventTicketSystem.util.EventValidationRules;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EventDAO implements EventRepository {

    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_LOCATION = "location";
    private static final String COLUMN_START = "start";
    private static final String COLUMN_STATUS = "status";

    private static final String EVENT_SELECT = """
            SELECT
                e.id,
                coordinator.user_id AS coordinator_id,
                e.name,
                e.location,
                e.location_guidance,
                e.notes,
                e.start_time,
                e.end_time,
                e.created_by_user_id,
                e.capacity,
                e.created_at,
                e.updated_at,
                e.is_deleted
            FROM dbo.Events e
            OUTER APPLY (
                SELECT TOP 1 ec.user_id
                FROM dbo.EventCoordinators ec
                WHERE ec.event_id = e.id
                  AND ec.removed_at IS NULL
                ORDER BY ec.assigned_at DESC, ec.id DESC
            ) coordinator
            """;

    private final Database database;

    public EventDAO(Database database) {
        this.database = database;
    }

    @Override
    public List<Event> getAllEvents() throws EventException {
        return searchAllEvents("all", "", true);
    }

    @Override
    public List<Event> getEventsForCoordinator(long coordinatorId) throws EventException {
        return searchEventsForCoordinator(coordinatorId, "all", "", true);
    }

    @Override
    public List<Event> searchAllEvents(String columnKey, String query, boolean includeDeleted) throws EventException {
        StringBuilder sql = new StringBuilder(EVENT_SELECT)
                .append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append(" AND e.is_deleted = 0");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY e.start_time ASC, e.id ASC");

        return queryEvents(sql.toString(), params);
    }

    @Override
    public List<Event> searchEventsForCoordinator(long coordinatorId,
                                                  String columnKey,
                                                  String query,
                                                  boolean includeDeleted) throws EventException {
        StringBuilder sql = new StringBuilder(EVENT_SELECT)
                .append("""
                         WHERE (
                             EXISTS (
                                 SELECT 1
                                 FROM dbo.EventCoordinators ec
                                 WHERE ec.event_id = e.id
                                   AND ec.user_id = ?
                                   AND ec.removed_at IS NULL
                             )
                             OR e.created_by_user_id = ?
                         )
                        """);
        List<Object> params = new ArrayList<>();
        params.add(coordinatorId);
        params.add(coordinatorId);

        if (!includeDeleted) {
            sql.append(" AND e.is_deleted = 0");
        }
        appendSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY e.start_time ASC, e.id ASC");

        return queryEvents(sql.toString(), params);
    }

    @Override
    public Event addEvent(Event event) throws EventException {
        if (event == null) {
            throw new EventException("Event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        try (Connection con = database.getConnection()) {
            con.setAutoCommit(false);
            try {
                validateEventState(con, event);
                long eventId = insertEvent(con, event);
                event.setId(eventId);
                saveTicketCategories(con, event);
                if (event.getCoordinatorId() != null && event.getCoordinatorId() > 0) {
                    attachCoordinatorIfNeeded(con, eventId, event.getCoordinatorId());
                }
                con.commit();
                return getEventById(eventId);
            } catch (SQLException | RuntimeException | EventException e) {
                con.rollback();
                throw e;
            }
        } catch (EventException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not add event.", e, EventException.ErrorType.DATABASE_ERROR);
        }
    }

    @Override
    public Event updateEvent(Event event) throws EventException {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new EventException("Valid event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }

        try (Connection con = database.getConnection()) {
            con.setAutoCommit(false);
            try {
                validateEventState(con, event);
                String sql = """
                        UPDATE dbo.Events
                        SET name = ?,
                            location = ?,
                            location_guidance = ?,
                            notes = ?,
                            start_time = ?,
                            end_time = ?,
                            created_by_user_id = ?,
                            capacity = ?,
                            updated_at = SYSDATETIME(),
                            is_deleted = ?
                        WHERE id = ?
                        """;

                try (PreparedStatement stmt = con.prepareStatement(sql)) {
                    bindEvent(stmt, event);
                    stmt.setLong(10, event.getId());
                    int rows = stmt.executeUpdate();
                    if (rows == 0) {
                        throw new EventException("Event not found.", EventException.ErrorType.NOT_FOUND);
                    }
                }

                saveTicketCategories(con, event);
                if (event.getCoordinatorId() != null && event.getCoordinatorId() > 0) {
                    attachCoordinatorIfNeeded(con, event.getId(), event.getCoordinatorId());
                }

                con.commit();
                return getEventById(event.getId());
            } catch (SQLException | RuntimeException | EventException e) {
                con.rollback();
                throw e;
            }
        } catch (EventException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not update event.", e, EventException.ErrorType.DATABASE_ERROR);
        }
    }

    @Override
    public void setEventDeleted(long eventId, long coordinatorId, boolean deleted) throws EventException {
        try (Connection con = database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement stmt = con.prepareStatement("""
                    UPDATE dbo.Events
                    SET is_deleted = ?,
                        updated_at = SYSDATETIME()
                    WHERE id = ?
                    """)) {
                stmt.setBoolean(1, deleted);
                stmt.setLong(2, eventId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    throw new EventException("Event not found.", EventException.ErrorType.NOT_FOUND);
                }
                if (deleted) {
                    setTicketsDeletedForEvent(con, eventId, true);
                }
                con.commit();
            } catch (SQLException | RuntimeException | EventException e) {
                con.rollback();
                throw e;
            }
        } catch (EventException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not update event.", e, EventException.ErrorType.DATABASE_ERROR);
        }
    }

    @Override
    public Event getEventById(long eventId) throws EventException {
        String sql = EVENT_SELECT + " WHERE e.id = ?";
        List<Object> params = List.of(eventId);
        List<Event> events = queryEvents(sql, params);
        if (events.isEmpty()) {
            throw new EventException("Event not found.", EventException.ErrorType.NOT_FOUND);
        }
        return events.get(0);
    }

    @Override
    public int countActiveTicketsForEvent(long eventId) throws EventException {
        try (Connection con = database.getConnection()) {
            return countActiveTicketsForEvent(con, eventId);
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not load active tickets.", e, EventException.ErrorType.DATABASE_ERROR);
        }
    }

    @Override
    public int countActiveTicketsForTicketCategory(long eventId, long ticketCategoryId) throws EventException {
        try (Connection con = database.getConnection()) {
            return countActiveTicketsForTicketCategory(con, eventId, ticketCategoryId);
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not load ticket type usage.", e, EventException.ErrorType.DATABASE_ERROR);
        }
    }

    private List<Event> queryEvents(String sql, List<Object> params) throws EventException {
        List<Event> events = new ArrayList<>();
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long eventId = rs.getLong("id");
                    events.add(DaoSupport.mapEvent(rs, loadTicketCategories(con, eventId)));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new EventException("Could not load events.", e, EventException.ErrorType.DATABASE_ERROR);
        }
        return events;
    }

    private long insertEvent(Connection con, Event event) throws SQLException {
        String sql = """
                INSERT INTO dbo.Events
                    (name, location, location_guidance, notes, start_time, end_time,
                     created_by_user_id, capacity, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindEvent(stmt, event);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert event.");
    }

    private void bindEvent(PreparedStatement stmt, Event event) throws SQLException {
        stmt.setString(1, event.getName());
        stmt.setString(2, event.getLocation());
        stmt.setString(3, event.getLocationGuidance());
        stmt.setString(4, event.getNotes());
        DaoSupport.setTimestampOrNull(stmt, 5, event.getStartTime());
        DaoSupport.setTimestampOrNull(stmt, 6, event.getEndTime());
        DaoSupport.setLongOrNull(stmt, 7, event.getCreatedByUserId());
        stmt.setInt(8, event.getCapacity());
        stmt.setBoolean(9, event.isDeleted());
    }

    private void saveTicketCategories(Connection con, Event event) throws SQLException {
        if (event.getTicketTypes() == null) {
            return;
        }

        for (TicketCategory category : event.getTicketTypes()) {
            if (category == null) {
                continue;
            }
            category.setEventId(event.getId());
            if (category.getId() == null || category.getId() <= 0) {
                insertTicketCategory(con, category);
            } else if (updateTicketCategory(con, category) == 0) {
                insertTicketCategory(con, category);
            }
        }
    }

    private void insertTicketCategory(Connection con, TicketCategory category) throws SQLException {
        String sql = """
                INSERT INTO dbo.TicketCategories
                    (event_id, name, price, seat_count, is_deleted)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindTicketCategory(stmt, category);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getLong(1));
                }
            }
        }
    }

    private int updateTicketCategory(Connection con, TicketCategory category) throws SQLException {
        String sql = """
                UPDATE dbo.TicketCategories
                SET name = ?,
                    price = ?,
                    seat_count = ?,
                    is_deleted = ?
                WHERE id = ?
                  AND event_id = ?
                """;
        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, category.getName());
            stmt.setBigDecimal(2, category.getPrice() == null ? BigDecimal.ZERO : category.getPrice());
            stmt.setInt(3, category.getSeatCount() == null ? 0 : category.getSeatCount());
            stmt.setBoolean(4, category.isDeleted());
            stmt.setLong(5, category.getId());
            stmt.setLong(6, category.getEventId());
            return stmt.executeUpdate();
        }
    }

    private void bindTicketCategory(PreparedStatement stmt, TicketCategory category) throws SQLException {
        stmt.setLong(1, category.getEventId());
        stmt.setString(2, category.getName());
        stmt.setBigDecimal(3, category.getPrice() == null ? BigDecimal.ZERO : category.getPrice());
        stmt.setInt(4, category.getSeatCount() == null ? 0 : category.getSeatCount());
        stmt.setBoolean(5, category.isDeleted());
    }

    private List<TicketCategory> loadTicketCategories(Connection con, long eventId) throws SQLException {
        List<TicketCategory> categories = new ArrayList<>();
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT id, event_id, name, price, seat_count, is_deleted, created_at
                FROM dbo.TicketCategories
                WHERE event_id = ?
                ORDER BY id
                """)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(DaoSupport.mapTicketCategory(rs));
                }
            }
        }
        return categories;
    }

    private void validateEventState(Connection con, Event event) throws SQLException, EventException {
        if (event == null) {
            throw new EventException("Event is required.", EventException.ErrorType.VALIDATION_ERROR);
        }
        if (event.getCapacity() < EventValidationRules.MIN_CAPACITY) {
            throw new EventException("Capacity must be at least " + EventValidationRules.MIN_CAPACITY + ".",
                    EventException.ErrorType.VALIDATION_ERROR);
        }
        if (EventValidationRules.countActiveTicketTypes(event.getTicketTypes()) == 0) {
            throw new EventException("At least one active ticket type is required.",
                    EventException.ErrorType.VALIDATION_ERROR);
        }

        int activeSeatCount = EventValidationRules.countActiveSeats(event.getTicketTypes());
        if (activeSeatCount != event.getCapacity()) {
            throw new EventException("Ticket type seats must match capacity exactly (allocated "
                    + activeSeatCount + " / capacity " + event.getCapacity() + ").",
                    EventException.ErrorType.VALIDATION_ERROR);
        }

        if (event.getId() == null || event.getId() <= 0) {
            return;
        }

        int activeTicketsForEvent = countActiveTicketsForEvent(con, event.getId());
        if (event.getCapacity() < activeTicketsForEvent) {
            throw new EventException("You cannot reduce capacity below " + activeTicketsForEvent + " active tickets.",
                    EventException.ErrorType.VALIDATION_ERROR);
        }

        List<TicketCategory> existingCategories = loadTicketCategories(con, event.getId());
        for (TicketCategory existingCategory : existingCategories) {
            if (existingCategory == null || existingCategory.getId() == null || existingCategory.getId() <= 0) {
                continue;
            }

            int activeTickets = countActiveTicketsForTicketCategory(con, event.getId(), existingCategory.getId());
            if (activeTickets <= 0) {
                continue;
            }

            TicketCategory updatedCategory = findCategoryById(event.getTicketTypes(), existingCategory.getId());
            String ticketTypeName = safeTicketTypeName(existingCategory);
            if (updatedCategory == null || updatedCategory.isDeleted()) {
                throw new EventException("You cannot delete ticket type '" + ticketTypeName
                        + "' because it already has " + activeTickets + " active tickets.",
                        EventException.ErrorType.VALIDATION_ERROR);
            }

            Integer updatedSeatCount = updatedCategory.getSeatCount();
            if (updatedSeatCount == null || updatedSeatCount < activeTickets) {
                throw new EventException("You cannot reduce ticket type '" + ticketTypeName
                        + "' below " + activeTickets + " active tickets.",
                        EventException.ErrorType.VALIDATION_ERROR);
            }
        }
    }

    private TicketCategory findCategoryById(List<TicketCategory> categories, Long categoryId) {
        if (categories == null || categoryId == null) {
            return null;
        }

        for (TicketCategory category : categories) {
            if (category != null && categoryId.equals(category.getId())) {
                return category;
            }
        }
        return null;
    }

    private int countActiveTicketsForEvent(Connection con, long eventId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT COUNT(*)
                FROM dbo.Tickets
                WHERE event_id = ?
                  AND deleted = 0
                """)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
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

    private void setTicketsDeletedForEvent(Connection con, long eventId, boolean deleted) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                UPDATE dbo.Tickets
                SET deleted = ?
                WHERE event_id = ?
                """)) {
            stmt.setBoolean(1, deleted);
            stmt.setLong(2, eventId);
            stmt.executeUpdate();
        }
    }

    private String safeTicketTypeName(TicketCategory category) {
        String name = category == null ? null : EventValidationRules.normalizeRequired(category.getName());
        return name == null || name.isEmpty() ? "selected ticket type" : name;
    }

    private void attachCoordinatorIfNeeded(Connection con, long eventId, long coordinatorId) throws SQLException {
        if (!isCoordinator(con, coordinatorId) || hasActiveCoordinatorAssignment(con, eventId, coordinatorId)) {
            return;
        }
        try (PreparedStatement stmt = con.prepareStatement("""
                INSERT INTO dbo.EventCoordinators (event_id, user_id)
                VALUES (?, ?)
                """)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, coordinatorId);
            stmt.executeUpdate();
        }
    }

    private boolean isCoordinator(Connection con, long userId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.Users
                WHERE id = ?
                  AND role_id = 2
                  AND is_deleted = 0
                """)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasActiveCoordinatorAssignment(Connection con, long eventId, long coordinatorId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.EventCoordinators
                WHERE event_id = ?
                  AND user_id = ?
                  AND removed_at IS NULL
                """)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, coordinatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
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
            case COLUMN_NAME -> "LOWER(e.name)";
            case COLUMN_LOCATION -> "LOWER(CONCAT(COALESCE(e.location, N''), N' ', COALESCE(e.location_guidance, N'')))";
            case COLUMN_START -> "LOWER(COALESCE(CONVERT(NVARCHAR(30), e.start_time, 120), N''))";
            case COLUMN_STATUS -> statusExpression();
            default -> "LOWER(CONCAT(e.name, N' ', COALESCE(e.location, N''), N' ', "
                    + "COALESCE(e.location_guidance, N''), N' ', COALESCE(e.notes, N''), N' ', "
                    + "COALESCE(CONVERT(NVARCHAR(30), e.start_time, 120), N''), N' ', "
                    + "CASE WHEN e.is_deleted = 1 THEN N'Deleted' ELSE N'Active' END))";
        };
    }

    private String statusExpression() {
        return "LOWER(CASE WHEN e.is_deleted = 1 THEN N'Deleted' ELSE N'Active' END)";
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long value) {
                stmt.setLong(i + 1, value);
            } else if (param instanceof Integer value) {
                stmt.setInt(i + 1, value);
            } else if (param == null) {
                stmt.setNull(i + 1, Types.NVARCHAR);
            } else {
                stmt.setString(i + 1, String.valueOf(param));
            }
        }
    }
}
