package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.exceptions.UserException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLogicTest {

    private final UserLogic userLogic = new UserLogic();

    @Test
    void shouldAuthenticateSeededAdminByEmail() throws UserException {
        assumeSeededDatabaseAvailable();
        User user = userLogic.authenticate("admin@easv.local", "admin1234");

        assertEquals("admin", user.getUsername());
        assertEquals("Sofie", user.getFirstName());
        assertTrue(user.hasRole(Role.ADMIN));
    }

    @Test
    void shouldRejectWrongPassword() {
        assumeSeededDatabaseAvailable();
        UserException exception = assertThrows(UserException.class,
                () -> userLogic.authenticate("admin@easv.local", "wrong-password"));

        assertEquals("Invalid username or password.", exception.getMessage());
    }

    private void assumeSeededDatabaseAvailable() {
        try {
            userLogic.authenticate("admin@easv.local", "admin1234");
        } catch (UserException ex) {
            Assumptions.assumeFalse(
                    "Could not authenticate against the database.".equals(ex.getMessage()),
                    "Seeded authentication database is not available in this environment."
            );
        }
    }
}
