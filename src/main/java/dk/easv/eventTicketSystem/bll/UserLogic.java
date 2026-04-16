package dk.easv.eventTicketSystem.bll;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.dal.repository.RepositoryProvider;
import dk.easv.eventTicketSystem.dal.repository.UserRepository;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.SessionManager;
import dk.easv.eventTicketSystem.util.UserValidationRules;
import dk.easv.eventTicketSystem.util.security.PasswordHasher;

import java.util.ArrayList;
import java.util.List;

public class UserLogic {

    private final UserRepository userRepository;

    public UserLogic() {
        this(RepositoryProvider.users());
    }

    public UserLogic(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
        requireActiveAdmin("Only admins can manage event coordinators.");
        userRepository.attachCoordinatorToEvent(eventId, userId);
    }

    public void detachCoordinatorFromEvent(long eventId, long userId) throws UserException {
        requireActiveAdmin("Only admins can manage event coordinators.");
        userRepository.detachCoordinatorFromEvent(eventId, userId);
    }

    public User createUser(User user) throws UserException {
        requireActiveAdmin("Only admins can manage users.");
        if (user == null) {
            throw new UserException("User is required.");
        }
        normalizeUser(user);
        validateUser(user, true);
        if (user.getRole() == null) {
            throw new UserException("Role is required.");
        }
        if (user.getPassword() != null && !user.getPassword().isBlank() && !PasswordHasher.isHashed(user.getPassword())) {
            user.setPassword(PasswordHasher.hash(user.getPassword()));
        }
        return userRepository.createUser(user);
    }

    public void updateUser(User user) throws UserException {
        requireActiveAdmin("Only admins can manage users.");
        if (user == null) {
            throw new UserException("User is required.");
        }
        if (user.getId() == null || user.getId() <= 0) {
            throw new UserException("Valid user is required.");
        }

        User existing = userRepository.getUserById(user.getId());
        normalizeUser(user);
        preserveExistingPasswordIfNeeded(user, existing);
        validateUser(user, false);
        enforceRoleAndDeleteRules(existing, user);

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

        User currentUser = SessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getId() != null && currentUser.getId().equals(updated.getId())) {
            SessionManager.setCurrentUser(updated.copy());
        }
    }

    public void setUserDeleted(long userId, boolean deleted) throws UserException {
        requireActiveAdmin("Only admins can manage users.");
        User user = getUserById(userId);
        user.setDeleted(deleted);
        updateUser(user);
    }

    public boolean existsByEmail(String email) throws UserException {
        return userRepository.existsByEmail(email);
    }

    private User requireActiveAdmin(String message) throws UserException {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null || currentUser.getId() <= 0) {
            throw new UserException(message);
        }

        User persisted;
        try {
            persisted = userRepository.getUserById(currentUser.getId());
        } catch (UserException e) {
            throw new UserException(message, e);
        }
        if (persisted.isDeleted() || !persisted.hasRole(Role.ADMIN)) {
            throw new UserException(message);
        }

        SessionManager.setCurrentUser(persisted.copy());
        return persisted;
    }

    private void normalizeUser(User user) {
        user.setUsername(UserValidationRules.normalizeRequired(user.getUsername()));
        user.setFirstName(UserValidationRules.normalizeRequired(user.getFirstName()));
        user.setLastName(UserValidationRules.normalizeRequired(user.getLastName()));
        user.setEmail(UserValidationRules.normalizeRequired(user.getEmail()));
        user.setPhone(UserValidationRules.normalizeRequired(user.getPhone()));
    }

    private void preserveExistingPasswordIfNeeded(User user, User existing) {
        if (user == null || existing == null) {
            return;
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(existing.getPassword());
        }
    }

    private void validateUser(User user, boolean create) throws UserException {
        if (user.getRole() == null) {
            throw new UserException("Role is required.");
        }

        validateRequiredField("Username", user.getUsername(), UserValidationRules.MAX_TEXT_LENGTH);
        validateRequiredField("First name", user.getFirstName(), UserValidationRules.MAX_TEXT_LENGTH);
        validateRequiredField("Last name", user.getLastName(), UserValidationRules.MAX_TEXT_LENGTH);
        validateRequiredField("Email", user.getEmail(), UserValidationRules.MAX_TEXT_LENGTH);

        if (!UserValidationRules.isValidEmail(user.getEmail())) {
            throw new UserException("Email must be a valid email address.");
        }
        if (!UserValidationRules.isValidPhone(user.getPhone())) {
            throw new UserException("Phone must contain only numbers.");
        }

        String password = user.getPassword();
        if (create && (password == null || password.isBlank())) {
            throw new UserException("Password is required.");
        }
        if (password != null && !password.isBlank() && !PasswordHasher.isHashed(password)
                && !UserValidationRules.isValidPassword(password)) {
            throw new UserException("Password must be at least " + UserValidationRules.MIN_PASSWORD_LENGTH + " characters.");
        }

        Long excludeUserId = create ? null : user.getId();
        if (userRepository.existsByUsername(user.getUsername(), excludeUserId)) {
            throw new UserException("Username already exists.");
        }
        if (userRepository.existsByEmail(user.getEmail(), excludeUserId)) {
            throw new UserException("Email already exists.");
        }
    }

    private void validateRequiredField(String label, String value, int maxLength) throws UserException {
        if (value == null || value.isBlank()) {
            throw new UserException(label + " is required.");
        }
        if (value.length() > maxLength) {
            throw new UserException(label + " is too long.");
        }
    }

    private void enforceRoleAndDeleteRules(User existing, User updated) throws UserException {
        boolean existingActiveAdmin = existing != null && existing.hasRole(Role.ADMIN) && !existing.isDeleted();
        boolean updatedActiveAdmin = updated.hasRole(Role.ADMIN) && !updated.isDeleted();
        if (existingActiveAdmin && !updatedActiveAdmin && userRepository.isOnlyActiveAdmin(existing.getId())) {
            throw new UserException(existing.isDeleted() || updated.isDeleted()
                    ? "You cannot delete the last active admin."
                    : "You cannot change the last active admin to coordinator.");
        }

        boolean existingCanCoordinate = existing != null && existing.hasRole(Role.COORDINATOR) && !existing.isDeleted();
        boolean updatedCanCoordinate = updated.hasRole(Role.COORDINATOR) && !updated.isDeleted();
        if (existingCanCoordinate && !updatedCanCoordinate) {
            List<String> blockingEvents = userRepository.findActiveEventsWhereUserIsOnlyCoordinator(existing.getId());
            if (!blockingEvents.isEmpty()) {
                String eventList = summarizeEvents(blockingEvents);
                if (updated.isDeleted()) {
                    throw new UserException("You cannot delete this coordinator because they are the only coordinator for: " + eventList + ".");
                }
                throw new UserException("You cannot change this coordinator to admin because they are the only coordinator for: " + eventList + ".");
            }
        }
    }

    private String summarizeEvents(List<String> eventNames) {
        List<String> names = eventNames == null ? List.of() : eventNames;
        if (names.isEmpty()) {
            return "";
        }

        List<String> trimmed = new ArrayList<>();
        for (String eventName : names) {
            String normalized = UserValidationRules.normalizeRequired(eventName);
            if (!normalized.isEmpty()) {
                trimmed.add(normalized);
            }
        }

        if (trimmed.isEmpty()) {
            return "the selected events";
        }
        if (trimmed.size() <= 3) {
            return String.join(", ", trimmed);
        }
        return String.join(", ", trimmed.subList(0, 3)) + " and " + (trimmed.size() - 3) + " more";
    }
}
