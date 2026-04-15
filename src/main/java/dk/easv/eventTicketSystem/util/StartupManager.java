package dk.easv.eventTicketSystem.util;

import dk.easv.eventTicketSystem.dal.ConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;

public class StartupManager {

    private final ConnectionManager conMan;

    public StartupManager(ConnectionManager conMan) {
        this.conMan = conMan;
    }

    public void initialize() throws SQLException {
        DatabaseInitializer dbInit = new DatabaseInitializer(conMan);
        dbInit.initializeAllTables();
    }

    public boolean canConnect() throws SQLException {
        try (Connection con = conMan.getConnection()) {
            return con.isValid(5);
        }
    }
}
