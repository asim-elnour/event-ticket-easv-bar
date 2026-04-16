package dk.easv.eventTicketSystem.gui.model;

import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.bll.UserLogic;
import dk.easv.eventTicketSystem.exceptions.UserException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class UserModel {

    private final UserLogic userLogic = new UserLogic();
    private final ObservableList<User> adminAndCoordinatorUsers = FXCollections.observableArrayList();
    private final ObservableList<User> coordinatorUsers = FXCollections.observableArrayList();
    private final SortedList<User> adminAndCoordinatorSorted = new SortedList<>(adminAndCoordinatorUsers);
    private final SortedList<User> coordinatorSorted = new SortedList<>(coordinatorUsers);
    private final AtomicLong adminUsersRequestVersion = new AtomicLong(0);
    private final AtomicLong coordinatorUsersRequestVersion = new AtomicLong(0);

    private boolean showDeletedAdminAndCoordinatorUsers = true;
    private boolean showDeletedCoordinatorUsers = true;
    private SearchModel.SearchState adminSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");
    private SearchModel.SearchState coordinatorSearchState = new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");

    public ObservableList<User> adminAndCoordinatorUsers() {
        return adminAndCoordinatorUsers;
    }

    public SortedList<User> adminAndCoordinatorUsersView() {
        return adminAndCoordinatorSorted;
    }

    public ObservableList<User> coordinatorUsers() {
        return coordinatorUsers;
    }

    public SortedList<User> coordinatorUsersView() {
        return coordinatorSorted;
    }

    public void applySearch(SearchModel.SearchState adminState, SearchModel.SearchState coordinatorState) {
        adminSearchState = normalizeState(adminState);
        coordinatorSearchState = normalizeState(coordinatorState);
    }

    public boolean isShowDeletedAdminAndCoordinatorUsers() {
        return showDeletedAdminAndCoordinatorUsers;
    }

    public void setShowDeletedAdminAndCoordinatorUsers(boolean showDeleted) {
        showDeletedAdminAndCoordinatorUsers = showDeleted;
    }

    public boolean isShowDeletedCoordinatorUsers() {
        return showDeletedCoordinatorUsers;
    }

    public void setShowDeletedCoordinatorUsers(boolean showDeleted) {
        showDeletedCoordinatorUsers = showDeleted;
    }

    public void loadAdminAndCoordinatorUsers() throws UserException {
        long requestVersion = adminUsersRequestVersion.incrementAndGet();
        List<User> loaded = userLogic.searchAdminAndCoordinatorUsers(
                adminSearchState.columnKey(),
                adminSearchState.query(),
                showDeletedAdminAndCoordinatorUsers
        );

        Platform.runLater(() -> {
            if (requestVersion == adminUsersRequestVersion.get()) {
                adminAndCoordinatorUsers.setAll(loaded);
            }
        });
    }

    public void loadCoordinatorUsers() throws UserException {
        long requestVersion = coordinatorUsersRequestVersion.incrementAndGet();
        List<User> loaded = userLogic.searchCoordinatorUsers(
                coordinatorSearchState.columnKey(),
                coordinatorSearchState.query(),
                showDeletedCoordinatorUsers
        );

        Platform.runLater(() -> {
            if (requestVersion == coordinatorUsersRequestVersion.get()) {
                coordinatorUsers.setAll(loaded);
            }
        });
    }

    public void loadCoordinatorUsersForEvent(long eventId) throws UserException {
        long requestVersion = coordinatorUsersRequestVersion.incrementAndGet();
        if (eventId <= 0) {
            Platform.runLater(() -> {
                if (requestVersion == coordinatorUsersRequestVersion.get()) {
                    coordinatorUsers.clear();
                }
            });
            return;
        }

        List<User> loaded = userLogic.searchCoordinatorUsersForEvent(
                eventId,
                coordinatorSearchState.columnKey(),
                coordinatorSearchState.query(),
                showDeletedCoordinatorUsers
        );
        Platform.runLater(() -> {
            if (requestVersion == coordinatorUsersRequestVersion.get()) {
                coordinatorUsers.setAll(loaded);
            }
        });
    }

    public void loadCoordinatorUsersForAllEvents() throws UserException {
        long requestVersion = coordinatorUsersRequestVersion.incrementAndGet();
        List<User> loaded = userLogic.searchCoordinatorUsers(
                coordinatorSearchState.columnKey(),
                coordinatorSearchState.query(),
                showDeletedCoordinatorUsers
        );
        Platform.runLater(() -> {
            if (requestVersion == coordinatorUsersRequestVersion.get()) {
                coordinatorUsers.setAll(loaded);
            }
        });
    }

    public User addCoordinator(User user) throws UserException {
        return userLogic.createUser(user);
    }

    public void addCoordinatorToEvent(User user, long eventId) throws UserException {
        if (user == null || user.getId() == null || eventId <= 0) {
            return;
        }
        userLogic.attachCoordinatorToEvent(eventId, user.getId());
    }

    public void removeCoordinatorFromEvent(User user, long eventId) throws UserException {
        if (user == null || user.getId() == null || eventId <= 0) {
            return;
        }
        userLogic.detachCoordinatorFromEvent(eventId, user.getId());
    }

    public void updateCoordinator(User user) throws UserException {
        userLogic.updateUser(user);
    }

    public void setCoordinatorDeleted(User user, boolean deleted) throws UserException {
        if (user == null) {
            throw new UserException("User is required.");
        }
        User candidate = user.copy();
        candidate.setDeleted(deleted);
        userLogic.updateUser(candidate);
    }

    public User addAdminOrCoordinator(User user) throws UserException {
        if (user == null) {
            throw new UserException("User is required.");
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new UserException("Phone is required.");
        }

        return userLogic.createUser(user);
    }

    public void updateAdminOrCoordinator(User user) throws UserException {
        userLogic.updateUser(user);
    }

    public void setAdminOrCoordinatorDeleted(User user, boolean deleted) throws UserException {
        if (user == null) {
            throw new UserException("User is required.");
        }
        User candidate = user.copy();
        candidate.setDeleted(deleted);
        userLogic.updateUser(candidate);
    }

    private SearchModel.SearchState normalizeState(SearchModel.SearchState state) {
        if (state == null) {
            return new SearchModel.SearchState(SearchModel.COLUMN_ALL, "");
        }
        return state;
    }
}
