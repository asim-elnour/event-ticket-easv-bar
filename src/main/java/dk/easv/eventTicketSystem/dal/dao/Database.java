package dk.easv.eventTicketSystem.dal.dao;

import dk.easv.eventTicketSystem.dal.ConnectionManager;
import dk.easv.eventTicketSystem.util.StartupManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class Database {

    private final ConnectionManager conMan;
    private final Object initializeLock = new Object();
    private volatile boolean initialized;

    public Database(ConnectionManager conMan) {
        this.conMan = conMan;
    }

    Connection getConnection() throws SQLException {
        initializeIfNeeded();
        return conMan.getConnection();
    }

    private void initializeIfNeeded() throws SQLException {
        if (initialized) {
            return;
        }

        synchronized (initializeLock) {
            if (initialized) {
                return;
            }

            StartupManager startupManager = new StartupManager(conMan);
            startupManager.initialize();
            initialized = true;
        }
    }
}
