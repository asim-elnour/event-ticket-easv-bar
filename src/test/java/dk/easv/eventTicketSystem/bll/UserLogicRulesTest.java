package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.UserRepository;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.SessionManager;
import dk.easv.eventTicketSystem.util.security.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLogicRulesTest {

    @AfterEach
    void clearSession() {
        SessionManager.clearCurrentUser();
    }

    @Test
    void shouldRejectCreateWhenCurrentUserIsNotAdmin() {
        FakeUserRepository repository = new FakeUserRepository();
        User coordinator = storedUser(2L, "coord", Role.COORDINATOR);
        repository.store(coordinator);
        SessionManager.setCurrentUser(coordinator.copy());

        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "new@example.com", "12345678", "password123", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Only admins can manage users.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectInvalidEmailWhenCreatingUser() {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "invalid-email", "12345678", "password123", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Email must be a valid email address.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectInvalidPhoneWhenCreatingUser() {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "new@example.com", "12-34", "password123", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Phone must contain only numbers.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectShortPasswordWhenCreatingUser() {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "new@example.com", "12345678", "short", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Password must be at least 8 characters.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectDuplicateUsernameWhenCreatingUser() {
        FakeUserRepository repository = adminRepository();
        repository.store(storedUser(2L, "coord", Role.COORDINATOR));
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("coord", "new@example.com", "12345678", "password123", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Username already exists.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectDuplicateEmailWhenCreatingUser() {
        FakeUserRepository repository = adminRepository();
        repository.store(storedUser(2L, "coord", Role.COORDINATOR));
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "coord@example.com", "12345678", "password123", Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.createUser(draft));

        assertEquals("Email already exists.", exception.getMessage());
        assertFalse(repository.createCalled);
    }

    @Test
    void shouldRejectDeletingLastActiveAdmin() {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User admin = repository.getStored(1L).copy();
        admin.setDeleted(true);

        UserException exception = assertThrows(UserException.class, () -> logic.updateUser(admin));

        assertEquals("You cannot delete the last active admin.", exception.getMessage());
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldRejectDemotingLastActiveAdmin() {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User admin = repository.getStored(1L).copy();
        admin.setRole(Role.COORDINATOR);

        UserException exception = assertThrows(UserException.class, () -> logic.updateUser(admin));

        assertEquals("You cannot change the last active admin to coordinator.", exception.getMessage());
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldRejectPromotingSoleCoordinatorToAdmin() {
        FakeUserRepository repository = adminRepository();
        User coordinator = storedUser(2L, "coord", Role.COORDINATOR);
        repository.store(coordinator);
        repository.setSoleCoordinatorEvents(2L, "Friday Bar");

        UserLogic logic = new UserLogic(repository);
        User edited = repository.getStored(2L).copy();
        edited.setRole(Role.ADMIN);

        UserException exception = assertThrows(UserException.class, () -> logic.updateUser(edited));

        assertEquals(
                "You cannot change this coordinator to admin because they are the only coordinator for: Friday Bar.",
                exception.getMessage()
        );
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldRejectDeletingSoleCoordinator() {
        FakeUserRepository repository = adminRepository();
        User coordinator = storedUser(2L, "coord", Role.COORDINATOR);
        repository.store(coordinator);
        repository.setSoleCoordinatorEvents(2L, "Friday Bar");

        UserLogic logic = new UserLogic(repository);
        User edited = repository.getStored(2L).copy();
        edited.setDeleted(true);

        UserException exception = assertThrows(UserException.class, () -> logic.updateUser(edited));

        assertEquals(
                "You cannot delete this coordinator because they are the only coordinator for: Friday Bar.",
                exception.getMessage()
        );
        assertFalse(repository.updateCalled);
    }

    @Test
    void shouldHashPasswordWhenCreatingUser() throws UserException {
        FakeUserRepository repository = adminRepository();
        UserLogic logic = new UserLogic(repository);
        User draft = draftUser("new-user", "new@example.com", "12345678", "password123", Role.COORDINATOR);

        logic.createUser(draft);

        assertTrue(repository.createCalled);
        assertTrue(PasswordHasher.isHashed(repository.lastCreatedUser.getPassword()));
        assertTrue(PasswordHasher.matches("password123", repository.lastCreatedUser.getPassword()));
    }

    @Test
    void shouldRejectCoordinatorAssignmentChangesForNonAdmin() {
        FakeUserRepository repository = new FakeUserRepository();
        User coordinator = storedUser(2L, "coord", Role.COORDINATOR);
        repository.store(coordinator);
        SessionManager.setCurrentUser(coordinator.copy());

        UserLogic logic = new UserLogic(repository);

        UserException exception = assertThrows(UserException.class, () -> logic.attachCoordinatorToEvent(10L, 2L));

        assertEquals("Only admins can manage event coordinators.", exception.getMessage());
        assertFalse(repository.attachCalled);
    }

    private static FakeUserRepository adminRepository() {
        FakeUserRepository repository = new FakeUserRepository();
        User admin = storedUser(1L, "admin", Role.ADMIN);
        repository.store(admin);
        SessionManager.setCurrentUser(admin.copy());
        return repository;
    }

    private static User draftUser(String username, String email, String phone, String password, Role role) {
        User user = new User(username, "Alice", "Example", email, password, role);
        user.setPhone(phone);
        user.setDeleted(false);
        return user;
    }

    private static User storedUser(long id, String username, Role role) {
        User user = new User(
                username,
                "Stored",
                "User",
                username + "@example.com",
                PasswordHasher.hash("password123"),
                role
        );
        user.idProperty().set(id);
        user.setPhone("12345678");
        user.setDeleted(false);
        return user;
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<Long, User> users = new HashMap<>();
        private final Map<Long, List<String>> soleCoordinatorEvents = new HashMap<>();
        private long nextId = 100L;

        private boolean createCalled;
        private boolean updateCalled;
        private boolean attachCalled;
        private User lastCreatedUser;

        void store(User user) {
            users.put(user.getId(), user.copy());
        }

        User getStored(long userId) {
            return users.get(userId).copy();
        }

        void setSoleCoordinatorEvents(long userId, String... eventNames) {
            soleCoordinatorEvents.put(userId, List.of(eventNames));
        }

        @Override
        public List<User> getAllUsers() {
            return new ArrayList<>(users.values());
        }

        @Override
        public List<User> getActiveUsers() {
            return users.values().stream().filter(user -> !user.isDeleted()).map(User::copy).toList();
        }

        @Override
        public User getUserById(long userId) throws UserException {
            User user = users.get(userId);
            if (user == null) {
                throw new UserException("User not found.");
            }
            return user.copy();
        }

        @Override
        public User authenticate(String usernameOrEmail, String rawPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> getCoordinatorUsersForEvent(long eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> getCoordinatorUsersForAllEvents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> searchAdminAndCoordinatorUsers(String columnKey, String query, boolean includeDeleted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> searchCoordinatorUsers(String columnKey, String query, boolean includeRemoved) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> searchCoordinatorUsersForEvent(long eventId, String columnKey, String query, boolean includeRemoved) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void attachCoordinatorToEvent(long eventId, long userId) {
            attachCalled = true;
        }

        @Override
        public void detachCoordinatorFromEvent(long eventId, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User createUser(User user) {
            createCalled = true;
            User stored = user.copy();
            if (stored.getId() == null) {
                stored.idProperty().set(nextId++);
            }
            users.put(stored.getId(), stored.copy());
            lastCreatedUser = stored.copy();
            return stored.copy();
        }

        @Override
        public User updateUser(User user) {
            updateCalled = true;
            users.put(user.getId(), user.copy());
            return user.copy();
        }

        @Override
        public boolean existsByEmail(String email) {
            return existsByEmail(email, null);
        }

        @Override
        public boolean existsByEmail(String email, Long excludeUserId) {
            String normalized = normalize(email);
            return users.values().stream().anyMatch(user ->
                    normalize(user.getEmail()).equals(normalized) && !sameId(user.getId(), excludeUserId));
        }

        @Override
        public boolean existsByUsername(String username) {
            return existsByUsername(username, null);
        }

        @Override
        public boolean existsByUsername(String username, Long excludeUserId) {
            String normalized = normalize(username);
            return users.values().stream().anyMatch(user ->
                    normalize(user.getUsername()).equals(normalized) && !sameId(user.getId(), excludeUserId));
        }

        @Override
        public boolean isOnlyActiveAdmin(long userId) {
            User user = users.get(userId);
            if (user == null || user.isDeleted() || !user.hasRole(Role.ADMIN)) {
                return false;
            }
            return users.values().stream()
                    .filter(candidate -> candidate.hasRole(Role.ADMIN) && !candidate.isDeleted())
                    .count() == 1;
        }

        @Override
        public List<String> findActiveEventsWhereUserIsOnlyCoordinator(long userId) {
            return new ArrayList<>(soleCoordinatorEvents.getOrDefault(userId, List.of()));
        }

        @Override
        public boolean isOnlyActiveCoordinatorForEvent(long eventId, long userId) {
            return false;
        }

        @Override
        public String getEventName(long eventId) {
            return "selected event";
        }

        private boolean sameId(Long left, Long right) {
            return left != null && right != null && left.equals(right);
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase();
        }
    }
}
