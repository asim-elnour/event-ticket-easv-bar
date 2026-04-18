package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.dal.ConnectionManager;
import dk.easv.eventTicketSystem.util.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    private final ConnectionManager conMan;

    public DatabaseInitializer(ConnectionManager conMan) {
        this.conMan = conMan;
    }

    public void initializeAllTables() throws SQLException {
        createRolesTable();
        seedRoles();
        createUsersTable();
        ensureUsersRoleColumn();
        createEventsTable();
        ensureEventsUpdatedAtColumn();
        createEventCoordinatorsTable();
        createTicketCategoriesTable();
        createCustomersTable();
        createTicketsTable();
        createIndexes();
        ensureDefaultAdminUser();
    }

    private void createRolesTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.Roles', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.Roles (
                        id INT NOT NULL CONSTRAINT PK_Roles PRIMARY KEY,
                        role_name NVARCHAR(100) NOT NULL,
                        role_code NVARCHAR(20) NOT NULL CONSTRAINT UQ_Roles_RoleCode UNIQUE
                    )
                END
                """);
    }

    private void seedRoles() throws SQLException {
        execute("""
                MERGE dbo.Roles AS target
                USING (VALUES
                    (1, N'Admin', N'A'),
                    (2, N'Event Coordinator', N'EC')
                ) AS source (id, role_name, role_code)
                ON target.id = source.id
                WHEN MATCHED THEN
                    UPDATE SET role_name = source.role_name, role_code = source.role_code
                WHEN NOT MATCHED THEN
                    INSERT (id, role_name, role_code)
                    VALUES (source.id, source.role_name, source.role_code);
                """);
    }

    private void createUsersTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.Users', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.Users (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Users PRIMARY KEY,
                        username NVARCHAR(255) NOT NULL CONSTRAINT UQ_Users_Username UNIQUE,
                        first_name NVARCHAR(255) NOT NULL,
                        last_name NVARCHAR(255) NOT NULL,
                        email NVARCHAR(255) NOT NULL CONSTRAINT UQ_Users_Email UNIQUE,
                        phone NVARCHAR(50) NULL,
                        password NVARCHAR(255) NOT NULL,
                        role_id INT NOT NULL CONSTRAINT FK_Users_Roles REFERENCES dbo.Roles(id),
                        is_deleted BIT NOT NULL CONSTRAINT DF_Users_IsDeleted DEFAULT 0,
                        is_locked BIT NOT NULL CONSTRAINT DF_Users_IsLocked DEFAULT 0,
                        created_at DATETIME2 NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSDATETIME()
                    )
                END
                """);
    }

    private void ensureUsersRoleColumn() throws SQLException {
        execute("""
                IF COL_LENGTH('dbo.Users', 'role_id') IS NULL
                BEGIN
                    ALTER TABLE dbo.Users ADD role_id INT NULL
                END
                """);

        execute("""
                IF OBJECT_ID(N'dbo.UserRoles', N'U') IS NOT NULL
                BEGIN
                    UPDATE users
                    SET role_id = active_roles.role_id
                    FROM dbo.Users users
                    CROSS APPLY (
                        SELECT TOP 1 user_roles.role_id
                        FROM dbo.UserRoles user_roles
                        WHERE user_roles.user_id = users.id
                          AND user_roles.removed_at IS NULL
                        ORDER BY user_roles.assigned_at DESC
                    ) active_roles
                    WHERE users.role_id IS NULL
                END
                """);

        execute("""
                UPDATE dbo.Users
                SET role_id = CASE
                    WHEN LOWER(username) = N'admin' THEN 1
                    ELSE 2
                END
                WHERE role_id IS NULL
                """);

        execute("""
                IF EXISTS (
                    SELECT 1
                    FROM sys.columns
                    WHERE object_id = OBJECT_ID(N'dbo.Users')
                      AND name = N'role_id'
                      AND is_nullable = 1
                )
                BEGIN
                    ALTER TABLE dbo.Users ALTER COLUMN role_id INT NOT NULL
                END
                """);

        execute("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.foreign_keys
                    WHERE name = N'FK_Users_Roles'
                      AND parent_object_id = OBJECT_ID(N'dbo.Users')
                )
                BEGIN
                    ALTER TABLE dbo.Users
                    ADD CONSTRAINT FK_Users_Roles FOREIGN KEY (role_id) REFERENCES dbo.Roles(id)
                END
                """);
    }

    private void createEventsTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.Events', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.Events (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Events PRIMARY KEY,
                        name NVARCHAR(255) NOT NULL,
                        location NVARCHAR(255) NULL,
                        location_guidance NVARCHAR(MAX) NULL,
                        notes NVARCHAR(MAX) NULL,
                        start_time DATETIME2 NULL,
                        end_time DATETIME2 NULL,
                        created_by_user_id BIGINT NULL CONSTRAINT FK_Events_Users REFERENCES dbo.Users(id),
                        created_at DATETIME2 NOT NULL CONSTRAINT DF_Events_CreatedAt DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL CONSTRAINT DF_Events_UpdatedAt DEFAULT SYSDATETIME(),
                        is_deleted BIT NOT NULL CONSTRAINT DF_Events_IsDeleted DEFAULT 0
                    )
                END
                """);
    }

    private void ensureEventsUpdatedAtColumn() throws SQLException {
        execute("""
                IF COL_LENGTH('dbo.Events', 'updated_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.Events ADD updated_at DATETIME2 NULL
                END
                """);

        execute("""
                UPDATE dbo.Events
                SET updated_at = COALESCE(CAST(created_at AS DATETIME2), SYSDATETIME())
                WHERE updated_at IS NULL
                """);

        execute("""
                IF EXISTS (
                    SELECT 1
                    FROM sys.columns
                    WHERE object_id = OBJECT_ID(N'dbo.Events')
                      AND name = N'updated_at'
                      AND is_nullable = 1
                )
                BEGIN
                    ALTER TABLE dbo.Events ALTER COLUMN updated_at DATETIME2 NOT NULL
                END
                """);
    }

    private void createEventCoordinatorsTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.EventCoordinators', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.EventCoordinators (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_EventCoordinators PRIMARY KEY,
                        event_id BIGINT NOT NULL CONSTRAINT FK_EventCoordinators_Events REFERENCES dbo.Events(id) ON DELETE CASCADE,
                        user_id BIGINT NOT NULL CONSTRAINT FK_EventCoordinators_Users REFERENCES dbo.Users(id),
                        assigned_at DATETIME2 NOT NULL CONSTRAINT DF_EventCoordinators_AssignedAt DEFAULT SYSDATETIME(),
                        removed_at DATETIME2 NULL
                    )
                END
                """);
    }

    private void createTicketCategoriesTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.TicketCategories', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.TicketCategories (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TicketCategories PRIMARY KEY,
                        event_id BIGINT NOT NULL CONSTRAINT FK_TicketCategories_Events REFERENCES dbo.Events(id) ON DELETE CASCADE,
                        name NVARCHAR(255) NOT NULL,
                        price DECIMAL(10,2) NOT NULL,
                        seat_count INT NOT NULL,
                        is_deleted BIT NOT NULL CONSTRAINT DF_TicketCategories_IsDeleted DEFAULT 0,
                        created_at DATETIME2 NOT NULL CONSTRAINT DF_TicketCategories_CreatedAt DEFAULT SYSDATETIME()
                    )
                END
                """);
    }

    private void createCustomersTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.Customers', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.Customers (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Customers PRIMARY KEY,
                        name NVARCHAR(255) NOT NULL,
                        email NVARCHAR(255) NOT NULL CONSTRAINT UQ_Customers_Email UNIQUE,
                        created_at DATETIME2 NOT NULL CONSTRAINT DF_Customers_CreatedAt DEFAULT SYSDATETIME()
                    )
                END
                """);
    }

    private void createTicketsTable() throws SQLException {
        execute("""
                IF OBJECT_ID(N'dbo.Tickets', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.Tickets (
                        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Tickets PRIMARY KEY,
                        event_id BIGINT NOT NULL CONSTRAINT FK_Tickets_Events REFERENCES dbo.Events(id) ON DELETE CASCADE,
                        ticket_category_id BIGINT NULL CONSTRAINT FK_Tickets_TicketCategories REFERENCES dbo.TicketCategories(id),
                        customer_id BIGINT NOT NULL CONSTRAINT FK_Tickets_Customers REFERENCES dbo.Customers(id),
                        code NVARCHAR(255) NOT NULL CONSTRAINT UQ_Tickets_Code UNIQUE,
                        issued_at DATETIME2 NOT NULL CONSTRAINT DF_Tickets_IssuedAt DEFAULT SYSDATETIME(),
                        redeemed_at DATETIME2 NULL,
                        redeemed BIT NOT NULL CONSTRAINT DF_Tickets_Redeemed DEFAULT 0,
                        refunded_at DATETIME2 NULL
                    )
                END
                """);
    }

    private void createIndexes() throws SQLException {
        execute("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = N'UX_EventCoordinators_Event_User_Active'
                      AND object_id = OBJECT_ID(N'dbo.EventCoordinators')
                )
                BEGIN
                    CREATE UNIQUE INDEX UX_EventCoordinators_Event_User_Active
                    ON dbo.EventCoordinators(event_id, user_id)
                    WHERE removed_at IS NULL
                END
                """);

        execute("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = N'IX_EventCoordinators_Event_User_Assigned'
                      AND object_id = OBJECT_ID(N'dbo.EventCoordinators')
                )
                BEGIN
                    CREATE INDEX IX_EventCoordinators_Event_User_Assigned
                    ON dbo.EventCoordinators(event_id ASC, user_id ASC, assigned_at DESC)
                END
                """);

        execute("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = N'IX_EventCoordinators_User_Event_Removed'
                      AND object_id = OBJECT_ID(N'dbo.EventCoordinators')
                )
                BEGIN
                    CREATE INDEX IX_EventCoordinators_User_Event_Removed
                    ON dbo.EventCoordinators(user_id, event_id, removed_at)
                END
                """);

        execute("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = N'IX_Tickets_Code'
                      AND object_id = OBJECT_ID(N'dbo.Tickets')
                )
                BEGIN
                    CREATE INDEX IX_Tickets_Code ON dbo.Tickets(code)
                END
                """);
    }

    private void ensureDefaultAdminUser() throws SQLException {
        String sql = """
                IF EXISTS (
                    SELECT 1
                    FROM dbo.Users
                    WHERE LOWER(username) = LOWER(?)
                )
                BEGIN
                    UPDATE dbo.Users
                    SET first_name = ?,
                        last_name = ?,
                        email = ?,
                        phone = ?,
                        password = ?,
                        role_id = ?,
                        is_deleted = 0,
                        is_locked = 0
                    WHERE LOWER(username) = LOWER(?)
                END
                ELSE
                BEGIN
                    INSERT INTO dbo.Users
                        (username, first_name, last_name, email, phone, password, role_id, is_deleted, is_locked)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, 0, 0)
                END
                """;

        try (Connection con = conMan.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            String username = "admin";
            String firstName = "System";
            String lastName = "Admin";
            String email = "admin@easv.com";
            String phone = "00000000";
            String passwordHash = PasswordHasher.hash("12345678");

            stmt.setString(1, username);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, email);
            stmt.setString(5, phone);
            stmt.setString(6, passwordHash);
            stmt.setInt(7, Role.ADMIN.getId());
            stmt.setString(8, username);
            stmt.setString(9, username);
            stmt.setString(10, firstName);
            stmt.setString(11, lastName);
            stmt.setString(12, email);
            stmt.setString(13, phone);
            stmt.setString(14, passwordHash);
            stmt.setInt(15, Role.ADMIN.getId());
            stmt.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection con = conMan.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        }
    }
}
