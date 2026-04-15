package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.dal.repository.UserRepository;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.security.PasswordHasher;

import java.util.List;

public class UserLogic {

    private final UserRepository userRepository = RepositoryProvider.users();

    public List<User> getAllUsers() throws UserException {
        return userRepository.getAllUsers();
    }

    public List<User> getActiveUsers() throws UserException {
        return userRepository.getActiveUsers();
    }

    public User getUserById(long userId) throws UserException {
        return userRepository.getUserById(userId);
    }

    public User authenticate(String usernameOrEmail, String rawPassword) throws UserException {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new UserException("Username/email and password are required.");
        }
        return userRepository.authenticate(usernameOrEmail, rawPassword);
    }

    public List<User> getCoordinatorUsersForEvent(long eventId) throws UserException {
        return userRepository.getCoordinatorUsersForEvent(eventId);
    }

    public List<User> getCoordinatorUsersForAllEvents() throws UserException {
        return userRepository.getCoordinatorUsersForAllEvents();
    }

    public List<User> searchAdminAndCoordinatorUsers(String columnKey, String query, boolean includeDeleted) throws UserException {
        return userRepository.searchAdminAndCoordinatorUsers(columnKey, query, includeDeleted);
    }

    public List<User> searchCoordinatorUsers(String columnKey, String query, boolean includeRemoved) throws UserException {
        return userRepository.searchCoordinatorUsers(columnKey, query, includeRemoved);
    }

    public List<User> searchCoordinatorUsersForEvent(long eventId,
                                                     String columnKey,
                                                     String query,
                                                     boolean includeRemoved) throws UserException {
        return userRepository.searchCoordinatorUsersForEvent(eventId, columnKey, query, includeRemoved);
    }

    public void attachCoordinatorToEvent(long eventId, long userId) throws UserException {
        userRepository.attachCoordinatorToEvent(eventId, userId);
    }

    public void detachCoordinatorFromEvent(long eventId, long userId) throws UserException {
        userRepository.detachCoordinatorFromEvent(eventId, userId);
    }

    public User createUser(User user) throws UserException {
        if (user == null) {
            throw new UserException("User is required.");
        }
        if (user.getRole() == null) {
            throw new UserException("Role is required.");
        }
        if (user.getPassword() != null && !user.getPassword().isBlank() && !PasswordHasher.isHashed(user.getPassword())) {
            user.setPassword(PasswordHasher.hash(user.getPassword()));
        }
        return userRepository.createUser(user);
    }

    public void updateUser(User user) throws UserException {
        if (user == null) {
            throw new UserException("User is required.");
        }
        if (user.getRole() == null) {
            throw new UserException("Role is required.");
        }
        if (user.getPassword() != null && !user.getPassword().isBlank() && !PasswordHasher.isHashed(user.getPassword())) {
            user.setPassword(PasswordHasher.hash(user.getPassword()));
        }

        User updated = userRepository.updateUser(user);
        user.setUsername(updated.getUsername());
        user.setFirstName(updated.getFirstName());
        user.setLastName(updated.getLastName());
        user.setEmail(updated.getEmail());
        user.setPhone(updated.getPhone());
        user.setPassword(updated.getPassword());
        user.setRole(updated.getRole());
        user.setDeleted(updated.isDeleted());
        user.setEventCoordinatorRemoved(updated.isEventCoordinatorRemoved());
        user.setEventCoordinatorEventId(updated.getEventCoordinatorEventId());
        user.setEventCoordinatorEventName(updated.getEventCoordinatorEventName());
        user.createdAtProperty().set(updated.getCreatedAt());
    }

    public void setUserDeleted(long userId, boolean deleted) throws UserException {
        User user = getUserById(userId);
        user.setDeleted(deleted);
        updateUser(user);
    }

    public boolean existsByEmail(String email) throws UserException {
        return userRepository.existsByEmail(email);
    }
}
