package dk.easv.eventTicketSystem.dal;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Properties;

public class ConnectionManager {

    private static final String CONFIG_FILE = "db.properties";

    private final Properties props = new Properties();

    public ConnectionManager() {
        loadProperties();
    }

    public Connection getConnection() throws SQLServerException {
        SQLServerDataSource ds = new SQLServerDataSource();

        ds.setServerName(requiredProperty("db.server"));
        ds.setPortNumber(Integer.parseInt(requiredProperty("db.port")));
        ds.setDatabaseName(requiredProperty("db.database"));
        ds.setUser(requiredProperty("db.user"));
        ds.setPassword(requiredProperty("db.password"));
        ds.setTrustServerCertificate(true);

        return ds.getConnection();
    }

    private void loadProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new IllegalStateException(CONFIG_FILE + " not found in src/main/resources.");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + CONFIG_FILE, e);
        }
    }

    private String requiredProperty(String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database property: " + key);
        }
        return value.trim();
    }
}
