package dk.easv.eventTicketSystem.gui.eventCoordinators;

import dk.easv.eventTicketSystem.be.Event;
import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.gui.model.DataViewMode;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.StatusBanner;
import dk.easv.eventTicketSystem.util.UserUiText;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.Locale;

public class EventCoordinatorsController implements ModelAware {

    @FXML private Label statusLabel;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUserName;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colEvent;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private Button showDeletedButton;
    @FXML private Button addButton;
    @FXML private Button removeButton;
    @FXML private ChoiceBox<DataViewMode> viewChoice;

    private final Label placeholderLabel = new Label("No coordinators found.");
    private final ListChangeListener<User> coordinatorsListener = change -> {
        updateShowDeletedButtonText();
        updatePlaceholder();
        restoreSelection();
    };

    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        placeholderLabel.getStyleClass().add("muted-text");

        colUserName.setText("Username");
        colFullName.setText("Name");
        colEvent.setText("Event");
        colStatus.setText("Status");
        removeButton.setText("Remove Coordinator");

        colUserName.setCellValueFactory(cd -> cd.getValue().usernameProperty());
        colFullName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFullName()));
        colEvent.setCellValueFactory(cd -> {
            User user = cd.getValue();
            if (user == null) {
                return new SimpleStringProperty("");
            }
            String eventName = user.getEventCoordinatorEventName();
            return new SimpleStringProperty(eventName == null ? "" : eventName);
        });
        colStatus.setCellValueFactory(cd -> {
            User user = cd.getValue();
            if (user == null) {
                return new SimpleStringProperty("");
            }
            return Bindings.createStringBinding(
                    () -> UserUiText.coordinatorStatusLabel(user),
                    user.deletedProperty(),
                    user.eventCoordinatorRemovedProperty()
            );
        });

        usersTable.setPlaceholder(placeholderLabel);
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldUser, newUser) -> {
            if (model != null) {
                model.setSelectedEventCoordinator(newUser);
            }
            updateActionState(newUser);
        });

        usersTable.setRowFactory(table -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                User item = row.getItem();
                if (model != null) {
                    model.setSelectedEventCoordinator(item);
                }
                updateActionState(item);
            });
            return row;
        });

        viewChoice.getItems().setAll(DataViewMode.values());
        viewChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (model == null || newValue == null || newValue == model.getCoordinatorViewMode()) {
                return;
            }
            model.setCoordinatorViewMode(newValue);
            reloadCoordinators();
        });

        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        updatePlaceholder();
        updateShowDeletedButtonText();
        updateActionState(null);

        if (model != null) {
            bindModel();
        }
    }

    @Override
    public void setModel(AppModel model) {
        this.model = model;
        bindModel();
    }

    private void bindModel() {
        if (model == null || usersTable == null) {
            return;
        }
        usersTable.setItems(model.coordinatorUsersView());
        model.coordinatorUsersView().comparatorProperty().bind(usersTable.comparatorProperty());
        model.coordinatorUsers().removeListener(coordinatorsListener);
        model.coordinatorUsers().addListener(coordinatorsListener);
        bindModelListeners();

        if (viewChoice.getValue() != model.getCoordinatorViewMode()) {
            viewChoice.setValue(model.getCoordinatorViewMode());
        }

        updateShowDeletedButtonText();
        updatePlaceholder();
        reloadCoordinators();
    }

    private void bindModelListeners() {
        if (modelListenersBound) {
            return;
        }
        model.selectedEventProperty().addListener((obs, oldValue, newValue) -> {
            if (model.getCoordinatorViewMode() == DataViewMode.SELECTED_EVENT) {
                reloadCoordinators();
            }
            updateActionState(usersTable.getSelectionModel().getSelectedItem());
        });
        model.currentEventIdProperty().addListener((obs, oldValue, newValue) -> {
            if (model.getCoordinatorViewMode() == DataViewMode.SELECTED_EVENT) {
                reloadCoordinators();
            }
            updateActionState(usersTable.getSelectionModel().getSelectedItem());
        });
        model.coordinatorViewModeProperty().addListener((obs, oldValue, newValue) -> {
            if (viewChoice.getValue() != newValue) {
                viewChoice.setValue(newValue);
            }
            updatePlaceholder();
            updateActionState(usersTable.getSelectionModel().getSelectedItem());
        });
        modelListenersBound = true;
    }

    private void reloadCoordinators() {
        usersTable.getSelectionModel().clearSelection();
        if (model != null) {
            model.coordinatorUsers().clear();
            model.setSelectedEventCoordinator(null);
        }
        updateActionState(null);
        updatePlaceholder();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (model.getCoordinatorViewMode() == DataViewMode.SELECTED_EVENT) {
                    model.loadCoordinatorUsersForEvent(model.getCurrentEventId());
                } else {
                    model.loadCoordinatorUsersForAllEvents();
                }
                return null;
            }
        };

        task.setOnFailed(event -> DialogUtils.showError("Load Coordinators", null,
                task.getException() == null ? "Unable to load coordinators." : task.getException().getMessage()));

        new Thread(task, "load-coordinators-task").start();
    }

    private void restoreSelection() {
        if (model == null || usersTable == null) {
            return;
        }

        User selected = model.getSelectedEventCoordinator();
        if (selected == null || selected.getId() == null) {
            updateActionState(null);
            return;
        }

        Long selectedEventId = selected.getEventCoordinatorEventId();
        for (User coordinator : model.coordinatorUsersView()) {
            if (coordinator == null || !selected.getId().equals(coordinator.getId())) {
                continue;
            }
            Long coordinatorEventId = coordinator.getEventCoordinatorEventId();
            if (selectedEventId == null || selectedEventId.equals(coordinatorEventId)) {
                usersTable.getSelectionModel().select(coordinator);
                usersTable.scrollTo(coordinator);
                updateActionState(coordinator);
                model.setSelectedEventCoordinator(coordinator);
                return;
            }
        }

        usersTable.getSelectionModel().clearSelection();
        model.setSelectedEventCoordinator(null);
        updateActionState(null);
    }

    @FXML
    private void onToggleShowDeletedCoordinators() {
        if (model == null) {
            return;
        }
        model.setShowDeletedCoordinatorUsers(!model.isShowDeletedCoordinatorUsers());
        updateShowDeletedButtonText();
        reloadCoordinators();
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedCoordinatorUsers()
                ? "Hide Deleted Coordinators"
                : "Show Deleted Coordinators");
    }

    @FXML
    private void onAddCoordinator() {
        if (!canManageCoordinators()) {
            showCoordinatorMessage("Coordinator Permissions", "Only admins can manage event coordinators.");
            return;
        }

        if (model == null) {
            return;
        }
        if (model.getCoordinatorViewMode() != DataViewMode.SELECTED_EVENT) {
            showCoordinatorMessage("Add Coordinator", "Switch to Selected Event view before adding a coordinator.");
            return;
        }

        long eventId = model.getCurrentEventId();
        if (eventId <= 0) {
            showCoordinatorMessage("Add Coordinator", "Please select an event first.");
            return;
        }

        Event selectedEvent = model.getSelectedEvent();
        if (selectedEvent != null && selectedEvent.isDeleted()) {
            showCoordinatorMessage("Add Coordinator", "Coordinators cannot be added to a deleted event.");
            return;
        }

        User selected = model.getSelectedUser();
        if (selected == null) {
            showCoordinatorMessage("Add Coordinator", "Please select a user first.");
            return;
        }

        if (selected.getId() == null || selected.getId() <= 0) {
            showCoordinatorMessage("Add Coordinator", "Please select a valid user first.");
            return;
        }

        if (selected.isDeleted()) {
            showCoordinatorMessage("Add Coordinator", "Selected user must be active.");
            return;
        }

        if (!selected.hasRole(Role.COORDINATOR)) {
            showCoordinatorMessage("Add Coordinator", "Selected user must have Event Coordinator role.");
            return;
        }

        User existing = findCoordinatorForEvent(selected.getId(), eventId);
        if (existing != null && !existing.isEventCoordinatorRemoved()) {
            String name = selected.getFullName().isBlank() ? selected.getUsername() : selected.getFullName();
            showCoordinatorMessage("Add Coordinator", name + " is already a coordinator for this event.");
            return;
        }

        String eventName = selectedEvent == null ? "selected event" : selectedEvent.getName();

        statusBanner.showSaving();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.addCoordinatorToEvent(selected, eventId);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            statusBanner.showSaved();
            reloadCoordinators();
        });

        task.setOnFailed(event -> {
            statusBanner.showFailed();
            showCoordinatorMessage("Add Coordinator",
                    task.getException() == null
                            ? "Unable to add coordinator to " + eventName + "."
                            : task.getException().getMessage());
        });

        new Thread(task, "add-coordinator-task").start();
    }

    @FXML
    private void onRemoveCoordinator() {
        if (!canManageCoordinators()) {
            showCoordinatorMessage("Coordinator Permissions", "Only admins can manage event coordinators.");
            return;
        }

        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            DialogUtils.showWarning("Remove Coordinator", null, "Please select a coordinator to remove.");
            return;
        }

        if (selected.isDeleted()) {
            showCoordinatorMessage("Coordinator", "Deleted users cannot be changed here.");
            return;
        }

        long eventId = selected.getEventCoordinatorEventId() == null
                ? (model == null ? 0L : model.getCurrentEventId())
                : selected.getEventCoordinatorEventId();
        if (eventId <= 0) {
            DialogUtils.showWarning("Coordinator", null, "Please select an event first.");
            return;
        }

        boolean shouldRemove = !selected.isEventCoordinatorRemoved();
        ActionDialogType dialogType = shouldRemove
                ? ActionDialogType.EVENT_COORDINATOR_REMOVE
                : ActionDialogType.EVENT_COORDINATOR_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getFullName(), resolveOwnerWindow())) {
            return;
        }

        statusBanner.showSaving();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (shouldRemove) {
                    model.removeCoordinatorFromEvent(selected, eventId);
                } else {
                    model.addCoordinatorToEvent(selected, eventId);
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            statusBanner.showSaved();
            reloadCoordinators();
        });

        task.setOnFailed(event -> {
            statusBanner.showFailed();
            showCoordinatorActionErrorDialog("Coordinator Update Failed", "We couldn't update this event coordinator right now.", task.getException());
        });

        new Thread(task, shouldRemove ? "remove-coordinator-task" : "restore-coordinator-task").start();
    }

    private record ErrorDialogDetails(String type, String error) {}

    private void showCoordinatorActionErrorDialog(String title, String message, Throwable throwable) {
        ErrorDialogDetails details = mapCoordinatorActionError(throwable);
        DialogUtils.showDetailedError(
                title,
                message,
                details.type(),
                details.error(),
                resolveOwnerWindow()
        );
    }

    private Window resolveOwnerWindow() {
        return usersTable != null && usersTable.getScene() != null
                ? usersTable.getScene().getWindow()
                : null;
    }

    private void showCoordinatorMessage(String title, String message) {
        DialogUtils.showMessageDialog(title, message, "OK", resolveOwnerWindow());
    }

    private User findCoordinatorForEvent(Long userId, long eventId) {
        if (userId == null || model == null || eventId <= 0) {
            return null;
        }
        for (User coordinator : model.coordinatorUsers()) {
            if (coordinator == null || !userId.equals(coordinator.getId())) {
                continue;
            }
            Long coordinatorEventId = coordinator.getEventCoordinatorEventId();
            if (coordinatorEventId != null && coordinatorEventId == eventId) {
                return coordinator;
            }
        }
        return null;
    }

    private ErrorDialogDetails mapCoordinatorActionError(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String technicalMessage = sanitizeMessage(root == null ? null : root.getMessage());
        if (technicalMessage == null) {
            technicalMessage = sanitizeMessage(throwable == null ? null : throwable.getMessage());
        }
        String normalized = technicalMessage == null ? "" : technicalMessage.toLowerCase(Locale.ROOT);

        String type;
        String detail;

        if (isDatabaseConnectionIssue(root, normalized)) {
            type = "Database Connection";
            detail = "Could not connect to the database.";
        } else if (isDataConflictIssue(root, normalized)) {
            type = "Data Conflict";
            detail = "The data conflicts with existing records.";
        } else if (containsAny(normalized, "permission", "denied", "forbidden", "not authorized", "unauthorized", "only admins")) {
            type = "Permission";
            detail = "You do not have permission to perform this action.";
        } else if (containsAny(normalized, "required", "valid", "must", "cannot", "only coordinator")) {
            type = "Validation";
            detail = "The request violates a validation or business rule.";
        } else if (containsAny(normalized, "not found", "no longer exists", "missing")) {
            type = "Coordinator Not Found";
            detail = "The selected coordinator record no longer exists.";
        } else if (root instanceof SQLException) {
            type = "Database Error";
            detail = "The database could not process this request.";
        } else {
            type = "Unexpected Error";
            detail = "Something unexpected happened while processing this request.";
        }

        return new ErrorDialogDetails(type, withTechnicalDetail(detail, technicalMessage));
    }

    private boolean isDatabaseConnectionIssue(Throwable root, String normalizedMessage) {
        if (containsAny(normalizedMessage, "connect", "connection", "timeout", "timed out", "socket", "network")) {
            return true;
        }
        if (!(root instanceof SQLException sqlException)) {
            return false;
        }
        String state = sqlException.getSQLState();
        return state != null && state.startsWith("08");
    }

    private boolean isDataConflictIssue(Throwable root, String normalizedMessage) {
        if (containsAny(normalizedMessage, "duplicate", "unique", "constraint", "already exists", "violat")) {
            return true;
        }
        if (!(root instanceof SQLException sqlException)) {
            return false;
        }
        String state = sqlException.getSQLState();
        int code = sqlException.getErrorCode();
        return code == 2601 || code == 2627 || (state != null && state.startsWith("23"));
    }

    private String withTechnicalDetail(String friendlyMessage, String technicalMessage) {
        if (technicalMessage == null || technicalMessage.isBlank()) {
            return friendlyMessage;
        }
        return friendlyMessage + " (" + abbreviate(technicalMessage, 180) + ")";
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard < 20) {
            current = current.getCause();
            guard++;
        }
        return current;
    }

    private void updateActionState(User selected) {
        boolean canManage = canManageCoordinators();
        boolean canAdd = canManage && canAddCoordinator();
        if (addButton != null) {
            addButton.setDisable(!canAdd);
        }

        if (selected == null) {
            removeButton.setDisable(true);
            removeButton.setText("Remove Coordinator");
            return;
        }
        removeButton.setDisable(!canManage || selected.isDeleted());
        removeButton.setText(selected.isEventCoordinatorRemoved()
                ? "Restore Coordinator"
                : "Remove Coordinator");
    }

    private boolean canManageCoordinators() {
        return model != null && model.isAdmin();
    }

    private boolean canAddCoordinator() {
        if (model == null || model.getCoordinatorViewMode() != DataViewMode.SELECTED_EVENT) {
            return false;
        }
        Event selectedEvent = model.getSelectedEvent();
        return selectedEvent != null
                && selectedEvent.getId() != null
                && selectedEvent.getId() > 0
                && !selectedEvent.isDeleted();
    }

    private void updatePlaceholder() {
        if (placeholderLabel == null || model == null) {
            return;
        }
        if (model.getCoordinatorViewMode() == DataViewMode.SELECTED_EVENT && model.getCurrentEventId() <= 0) {
            placeholderLabel.setText("Select an event to view coordinators.");
            return;
        }
        placeholderLabel.setText("No coordinators found.");
    }
}
