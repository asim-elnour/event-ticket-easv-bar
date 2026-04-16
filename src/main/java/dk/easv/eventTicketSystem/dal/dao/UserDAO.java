package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.UserRepository;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class UserDAO implements UserRepository {

    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_ROLE = "role";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_EVENT = "event";

    private static final String USER_SELECT = """
            SELECT
                u.id,
                u.username,
                u.first_name,
                u.last_name,
                u.email,
                u.phone,
                u.password,
                u.role_id,
                u.is_deleted,
                u.created_at,
                coordinator.event_id AS coordinator_event_id,
                e.name AS coordinator_event_name,
                CAST(CASE
                    WHEN coordinator.id IS NOT NULL AND coordinator.removed_at IS NOT NULL THEN 1
                    ELSE 0
                END AS bit) AS coordinator_removed
            FROM dbo.Users u
            OUTER APPLY (
                SELECT TOP 1 ec.id, ec.event_id, ec.removed_at, ec.assigned_at
                FROM dbo.EventCoordinators ec
                WHERE ec.user_id = u.id
                ORDER BY
                    CASE WHEN ec.removed_at IS NULL THEN 0 ELSE 1 END,
                    ec.assigned_at DESC,
                    ec.id DESC
            ) coordinator
            LEFT JOIN dbo.Events e ON e.id = coordinator.event_id
            """;

    private static final String COORDINATOR_FOR_EVENT_SELECT = """
            SELECT
                u.id,
                u.username,
                u.first_name,
                u.last_name,
                u.email,
                u.phone,
                u.password,
                u.role_id,
                u.is_deleted,
                u.created_at,
                coordinator.event_id AS coordinator_event_id,
                e.name AS coordinator_event_name,
                CAST(CASE
                    WHEN coordinator.removed_at IS NOT NULL THEN 1
                    ELSE 0
                END AS bit) AS coordinator_removed
            FROM dbo.Users u
            JOIN (
                SELECT id, event_id, user_id, removed_at, assigned_at
                FROM (
                    SELECT
                        ec.id,
                        ec.event_id,
                        ec.user_id,
                        ec.removed_at,
                        ec.assigned_at,
                        ROW_NUMBER() OVER (
                            PARTITION BY ec.event_id, ec.user_id
                            ORDER BY ec.assigned_at DESC, ec.id DESC
                        ) AS rn
                    FROM dbo.EventCoordinators ec
                    WHERE ec.event_id = ?
                ) ranked
                WHERE ranked.rn = 1
            ) coordinator ON coordinator.user_id = u.id
            LEFT JOIN dbo.Events e ON e.id = coordinator.event_id
            """;

    private final Database database;

    public UserDAO(Database database) {
        this.database = database;
    }

    @Override
    public List<User> getAllUsers() throws UserException {
        return searchAdminAndCoordinatorUsers("all", "", true);
    }

    @Override
    public List<User> getActiveUsers() throws UserException {
        return searchAdminAndCoordinatorUsers("all", "", false);
    }

    @Override
    public User getUserById(long userId) throws UserException {
        String sql = USER_SELECT + " WHERE u.id = ?";
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return DaoSupport.mapUser(rs);
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not load user.", e);
        }
        throw new UserException("User not found.");
    }

    @Override
    public User authenticate(String usernameOrEmail, String rawPassword) throws UserException {
        String sql = USER_SELECT + """
                 WHERE u.is_deleted = 0
                   AND u.is_locked = 0
                   AND (LOWER(u.username) = ? OR LOWER(u.email) = ?)
                """;
        String identifier = DaoSupport.safe(usernameOrEmail).toLowerCase();

        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, identifier);
            stmt.setString(2, identifier);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = DaoSupport.mapUser(rs);
                    if (PasswordHasher.matches(rawPassword, user.getPassword())) {
                        return user;
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not authenticate against the database.", e);
        }

        throw new UserException("Invalid username or password.");
    }

    @Override
    public List<User> getCoordinatorUsersForEvent(long eventId) throws UserException {
        return searchCoordinatorUsersForEvent(eventId, "all", "", true);
    }

    @Override
    public List<User> getCoordinatorUsersForAllEvents() throws UserException {
        return searchCoordinatorUsers("all", "", true);
    }

    @Override
    public List<User> searchAdminAndCoordinatorUsers(String columnKey, String query, boolean includeDeleted)
            throws UserException {
        StringBuilder sql = new StringBuilder(USER_SELECT)
                .append(" WHERE u.role_id IN (?, ?)");
        List<Object> params = new ArrayList<>();
        params.add(Role.ADMIN.getId());
        params.add(Role.COORDINATOR.getId());

        if (!includeDeleted) {
            sql.append(" AND u.is_deleted = 0");
        }
        appendAdminSearch(sql, params, columnKey, query);
        sql.append(" ORDER BY LOWER(u.username), u.id");

        return queryUsers(sql.toString(), params);
    }

    @Override
    public List<User> searchCoordinatorUsers(String columnKey, String query, boolean includeRemoved)
            throws UserException {
        StringBuilder sql = new StringBuilder(USER_SELECT)
                .append(" WHERE u.role_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(Role.COORDINATOR.getId());

        if (!includeRemoved) {
            sql.append(" AND (coordinator.id IS NULL OR coordinator.removed_at IS NULL)");
        }
        appendCoordinatorSearch(sql, params, columnKey, query, "coordinator");
        sql.append(" ORDER BY LOWER(u.username), u.id");

        return queryUsers(sql.toString(), params);
    }

    @Override
    public List<User> searchCoordinatorUsersForEvent(long eventId,
                                                     String columnKey,
                                                     String query,
                                                     boolean includeRemoved) throws UserException {
        ensureEventExists(eventId);

        StringBuilder sql = new StringBuilder(COORDINATOR_FOR_EVENT_SELECT)
                .append(" WHERE u.role_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(eventId);
        params.add(Role.COORDINATOR.getId());

        if (!includeRemoved) {
            sql.append(" AND coordinator.removed_at IS NULL");
        }
        appendCoordinatorSearch(sql, params, columnKey, query, "coordinator");
        sql.append(" ORDER BY LOWER(u.username), u.id");

        return queryUsers(sql.toString(), params);
    }

    @Override
    public void attachCoordinatorToEvent(long eventId, long userId) throws UserException {
        try (Connection con = database.getConnection()) {
            con.setAutoCommit(false);
            try {
                ensureCoordinator(con, userId);
                ensureEvent(con, eventId);

                if (hasActiveCoordinatorAssignment(con, eventId, userId)) {
                    con.commit();
                    return;
                }

                try (PreparedStatement stmt = con.prepareStatement("""
                        INSERT INTO dbo.EventCoordinators (event_id, user_id)
                        VALUES (?, ?)
                        """)) {
                    stmt.setLong(1, eventId);
                    stmt.setLong(2, userId);
                    stmt.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not attach coordinator to event.", e);
        }
    }

    @Override
    public void detachCoordinatorFromEvent(long eventId, long userId) throws UserException {
        try (Connection con = database.getConnection()) {
            ensureCoordinator(con, userId);
            ensureEvent(con, eventId);

            try (PreparedStatement stmt = con.prepareStatement("""
                    UPDATE dbo.EventCoordinators
                    SET removed_at = SYSDATETIME()
                    WHERE event_id = ?
                      AND user_id = ?
                      AND removed_at IS NULL
                    """)) {
                stmt.setLong(1, eventId);
                stmt.setLong(2, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not remove coordinator from event.", e);
        }
    }

    @Override
    public User createUser(User user) throws UserException {
        if (user == null || user.getRole() == null) {
            throw new UserException("User and role are required.");
        }

        String sql = """
                INSERT INTO dbo.Users
                    (username, first_name, last_name, email, phone, password, role_id, is_deleted, is_locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;

        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindUserForSave(stmt, user);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return getUserById(keys.getLong(1));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not create user.", e);
        }

        throw new UserException("Could not create user.");
    }

    @Override
    public User updateUser(User user) throws UserException {
        if (user == null || user.getId() == null || user.getId() <= 0 || user.getRole() == null) {
            throw new UserException("Valid user and role are required.");
        }

        try (Connection con = database.getConnection()) {
            con.setAutoCommit(false);
            try {
                String sql = """
                        UPDATE dbo.Users
                        SET username = ?,
                            first_name = ?,
                            last_name = ?,
                            email = ?,
                            phone = ?,
                            password = ?,
                            role_id = ?,
                            is_deleted = ?
                        WHERE id = ?
                        """;

                try (PreparedStatement stmt = con.prepareStatement(sql)) {
                    int index = bindUserForSave(stmt, user);
                    stmt.setLong(index, user.getId());
                    int rows = stmt.executeUpdate();
                    if (rows == 0) {
                        throw new UserException("User not found.");
                    }
                }

                if (!user.hasRole(Role.COORDINATOR)) {
                    removeActiveAssignments(con, user.getId());
                }

                con.commit();
            } catch (SQLException | RuntimeException | UserException e) {
                con.rollback();
                throw e;
            }
        } catch (UserException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not update user.", e);
        }

        return getUserById(user.getId());
    }

    @Override
    public boolean existsByEmail(String email) throws UserException {
        String sql = "SELECT 1 FROM dbo.Users WHERE LOWER(email) = ?";
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, DaoSupport.safe(email).toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not check email.", e);
        }
    }

    private List<User> queryUsers(String sql, List<Object> params) throws UserException {
        List<User> users = new ArrayList<>();
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(DaoSupport.mapUser(rs));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Could not load users.", e);
        }
        return users;
    }

    private void appendAdminSearch(StringBuilder sql, List<Object> params, String columnKey, String query) {
        if (DaoSupport.isBlank(query)) {
            return;
        }
        sql.append(" AND ").append(adminSearchExpression(columnKey)).append(" LIKE ?");
        params.add(DaoSupport.likePattern(query));
    }

    private String adminSearchExpression(String columnKey) {
        String normalized = DaoSupport.safe(columnKey);
        return switch (normalized) {
            case COLUMN_USERNAME -> "LOWER(u.username)";
            case COLUMN_NAME -> "LOWER(CONCAT(u.first_name, N' ', u.last_name))";
            case COLUMN_ROLE -> roleExpression();
            case COLUMN_STATUS -> userStatusExpression();
            default -> "LOWER(CONCAT(u.username, N' ', u.first_name, N' ', u.last_name, N' ', u.email, N' ', "
                    + roleExpressionRaw() + ", N' ', " + userStatusExpressionRaw() + "))";
        };
    }

    private void appendCoordinatorSearch(StringBuilder sql,
                                         List<Object> params,
                                         String columnKey,
                                         String query,
                                         String coordinatorAlias) {
        if (DaoSupport.isBlank(query)) {
            return;
        }
        sql.append(" AND ").append(coordinatorSearchExpression(columnKey, coordinatorAlias)).append(" LIKE ?");
        params.add(DaoSupport.likePattern(query));
    }

    private String coordinatorSearchExpression(String columnKey, String coordinatorAlias) {
        String normalized = DaoSupport.safe(columnKey);
        String eventName = "LOWER(COALESCE(e.name, N''))";
        String status = "LOWER(CASE WHEN " + coordinatorAlias + ".id IS NOT NULL AND "
                + coordinatorAlias + ".removed_at IS NOT NULL THEN N'Removed' ELSE N'Active' END)";
        return switch (normalized) {
            case COLUMN_USERNAME -> "LOWER(u.username)";
            case COLUMN_NAME -> "LOWER(CONCAT(u.first_name, N' ', u.last_name))";
            case COLUMN_EVENT -> eventName;
            case COLUMN_STATUS -> status;
            default -> "LOWER(CONCAT(u.username, N' ', u.first_name, N' ', u.last_name, N' ', u.email, N' ', "
                    + "COALESCE(e.name, N''), N' ', "
                    + "CASE WHEN " + coordinatorAlias + ".id IS NOT NULL AND "
                    + coordinatorAlias + ".removed_at IS NOT NULL THEN N'Removed' ELSE N'Active' END))";
        };
    }

    private String roleExpression() {
        return "LOWER(" + roleExpressionRaw() + ")";
    }

    private String roleExpressionRaw() {
        return "CASE u.role_id WHEN 1 THEN N'Admin' WHEN 2 THEN N'Event Coordinator' ELSE N'' END";
    }

    private String userStatusExpression() {
        return "LOWER(" + userStatusExpressionRaw() + ")";
    }

    private String userStatusExpressionRaw() {
        return "CASE WHEN u.is_deleted = 1 THEN N'Deleted' ELSE N'Active' END";
    }

    private int bindUserForSave(PreparedStatement stmt, User user) throws SQLException {
        stmt.setString(1, user.getUsername());
        stmt.setString(2, user.getFirstName());
        stmt.setString(3, user.getLastName());
        stmt.setString(4, user.getEmail());
        stmt.setString(5, user.getPhone());
        stmt.setString(6, user.getPassword());
        stmt.setInt(7, user.getRole().getId());
        stmt.setBoolean(8, user.isDeleted());
        return 9;
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Integer value) {
                stmt.setInt(i + 1, value);
            } else if (param instanceof Long value) {
                stmt.setLong(i + 1, value);
            } else {
                stmt.setString(i + 1, String.valueOf(param));
            }
        }
    }

    private void ensureEventExists(long eventId) throws UserException {
        try (Connection con = database.getConnection()) {
            ensureEvent(con, eventId);
        } catch (SQLException | RuntimeException e) {
            throw new UserException("Event not found.", e);
        }
    }

    private void ensureEvent(Connection con, long eventId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("SELECT 1 FROM dbo.Events WHERE id = ?")) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Event not found.");
                }
            }
        }
    }

    private void ensureCoordinator(Connection con, long userId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.Users
                WHERE id = ?
                  AND role_id = ?
                  AND is_deleted = 0
                """)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, Role.COORDINATOR.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Only coordinator users can be attached to events.");
                }
            }
        }
    }

    private boolean hasActiveCoordinatorAssignment(Connection con, long eventId, long userId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                SELECT 1
                FROM dbo.EventCoordinators
                WHERE event_id = ?
                  AND user_id = ?
                  AND removed_at IS NULL
                """)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void removeActiveAssignments(Connection con, long userId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("""
                UPDATE dbo.EventCoordinators
                SET removed_at = SYSDATETIME()
                WHERE user_id = ?
                  AND removed_at IS NULL
                """)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        }
    }
}
