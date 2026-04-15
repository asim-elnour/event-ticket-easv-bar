package dk.easv.eventTicketSystem.dal.repository;

import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.exceptions.UserException;

import java.util.List;

public interface UserRepository {

    List<User> getAllUsers() throws UserException;

    List<User> getActiveUsers() throws UserException;

    User getUserById(long userId) throws UserException;

    User authenticate(String usernameOrEmail, String rawPassword) throws UserException;

    List<User> getCoordinatorUsersForEvent(long eventId) throws UserException;

    List<User> getCoordinatorUsersForAllEvents() throws UserException;

    List<User> searchAdminAndCoordinatorUsers(String columnKey, String query, boolean includeDeleted) throws UserException;

    List<User> searchCoordinatorUsers(String columnKey, String query, boolean includeRemoved) throws UserException;

    List<User> searchCoordinatorUsersForEvent(long eventId, String columnKey, String query, boolean includeRemoved) throws UserException;

    void attachCoordinatorToEvent(long eventId, long userId) throws UserException;

    void detachCoordinatorFromEvent(long eventId, long userId) throws UserException;

    User createUser(User user) throws UserException;

    User updateUser(User user) throws UserException;

    boolean existsByEmail(String email) throws UserException;
}
