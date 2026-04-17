package dk.easv.eventTicketSystem.gui.adminsAndCoordinators;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.gui.ModelAware;
import dk.easv.eventTicketSystem.gui.common.ActionDialogType;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.SceneNavigator;
import dk.easv.eventTicketSystem.util.SessionManager;
import dk.easv.eventTicketSystem.util.StatusBanner;
import dk.easv.eventTicketSystem.util.UserUiText;
import dk.easv.eventTicketSystem.util.ViewType;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class AdminsAndCoordinatorsController implements ModelAware {

    @FXML private Label sectionTitleLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUserName;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private Button showDeletedButton;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deactivateButton;

    private AppModel model;
    private StatusBanner statusBanner;
    private boolean modelListenersBound;

    @FXML
    public void initialize() {
        statusBanner = new StatusBanner(statusLabel);
        initializeStaticTexts();

        colUserName.setCellValueFactory(cd -> cd.getValue().usernameProperty());
        colFullName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFullName()));
        colRole.setCellValueFactory(cd -> new SimpleStringProperty(UserUiText.roleLabel(cd.getValue().getRole())));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(UserUiText.statusLabel(cd.getValue())));

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldUser, newUser) -> {
            if (model != null && newUser != null) {
                model.setSelectedUser(newUser);
            }
            updateActionState(newUser);
        });

        usersTable.setRowFactory(table -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                User item = row.getItem();
                if (item != null && model != null) {
                    model.setSelectedUser(item);
                    updateActionState(item);
                }
            });
            return row;
        });

        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        updateActionState(null);

        if (model != null) {
            bindModel();
        }
    }

    private void initializeStaticTexts() {
        sectionTitleLabel.setText("Users");
        colFullName.setText("Name");
        colUserName.setText("Username");
        colRole.setText("Role");
        colStatus.setText("Status");
        addButton.setText("Add User");
        editButton.setText("Edit User");
        deactivateButton.setText("Deactivate User");
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
        usersTable.setItems(model.adminAndCoordinatorUsersView());
        model.adminAndCoordinatorUsersView().comparatorProperty().bind(usersTable.comparatorProperty());
        bindModelListeners();
        updateShowDeletedButtonText();
        reloadUsers();
    }

    private void bindModelListeners() {
        if (modelListenersBound) {
            return;
        }
        model.adminAndCoordinatorUsersView().addListener((ListChangeListener<User>) change -> restoreSelection());
        modelListenersBound = true;
    }

    @FXML
    private void onToggleShowDeletedUsers() {
        if (model == null) {
            return;
        }

        model.setShowDeletedAdminAndCoordinatorUsers(!model.isShowDeletedAdminAndCoordinatorUsers());
        updateShowDeletedButtonText();
        reloadUsers();
    }

    private void updateShowDeletedButtonText() {
        if (showDeletedButton == null || model == null) {
            return;
        }
        showDeletedButton.setText(model.isShowDeletedAdminAndCoordinatorUsers()
                ? "Hide Deleted Users"
                : "Show Deleted Users");
    }

    @FXML
    private void onAddUser() {
        if (!canManageUsers()) {
            showMessageDialog("User Permissions", "Only admins can manage users.");
            return;
        }

        Optional<User> result = showUserDialog(null);
        result.ifPresent(user -> {
            statusBanner.showSaving();
            Task<User> task = new Task<>() {
                @Override
                protected User call() throws Exception {
                    return model.addAdminOrCoordinator(user);
                }
            };
            task.setOnSucceeded(event -> {
                statusBanner.showSaved();
                reloadUsers();
            });
            task.setOnFailed(event -> {
                statusBanner.showFailed();
                showUserActionErrorDialog("Add User Failed", "We couldn't add this user right now.", task.getException());
            });
            new Thread(task, "add-user-task").start();
        });
    }

    @FXML
    private void onEditUser() {
        if (!canManageUsers()) {
            showMessageDialog("User Permissions", "Only admins can manage users.");
            return;
        }

        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessageDialog("Edit User", "Please select a user to edit.");
            return;
        }

        Optional<User> result = showUserDialog(selected);
        result.ifPresent(user -> {
            boolean shouldForceLogout = isCurrentSessionUser(user)
                    && (user.isDeleted() || !user.hasRole(Role.ADMIN));
            statusBanner.showSaving();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    model.updateAdminOrCoordinator(user);
                    return null;
                }
            };
            task.setOnSucceeded(event -> {
                statusBanner.showSaved();
                if (shouldForceLogout) {
                    forceLogout("Your account permissions changed. Please log in again.");
                    return;
                }
                reloadUsers();
            });
            task.setOnFailed(event -> {
                statusBanner.showFailed();
                showUserActionErrorDialog("Edit User Failed", "We couldn't save changes for this user.", task.getException());
            });
            new Thread(task, "edit-user-task").start();
        });
    }

    @FXML
    private void onDeactivateUser() {
        if (!canManageUsers()) {
            showMessageDialog("User Permissions", "Only admins can manage users.");
            return;
        }

        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessageDialog("User State", "Please select a user first.");
            return;
        }

        boolean shouldDelete = !selected.isDeleted();
        ActionDialogType dialogType = shouldDelete ? ActionDialogType.USER_DEACTIVATE : ActionDialogType.USER_RESTORE;
        if (!DialogUtils.confirmAction(dialogType, selected.getFullName(), resolveOwnerWindow())) {
            return;
        }

        statusBanner.showSaving();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.setAdminOrCoordinatorDeleted(selected, shouldDelete);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            statusBanner.showSaved();
            if (shouldDelete && isCurrentSessionUser(selected)) {
                forceLogout("Your account was deactivated. Please log in again.");
                return;
            }
            updateActionState(selected);
            reloadUsers();
        });
        task.setOnFailed(event -> {
            statusBanner.showFailed();
            showUserActionErrorDialog("User Update Failed", "We couldn't update this user right now.", task.getException());
        });
        new Thread(task, (shouldDelete ? "deactivate" : "restore") + "-user-task").start();
    }

    private void reloadUsers() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                model.loadAdminAndCoordinatorUsers();
                return null;
            }
        };

        task.setOnFailed(event -> showMessageDialog(
                "Load Users",
                task.getException() == null ? "Unable to load users." : task.getException().getMessage()
        ));

        new Thread(task, "load-users-task").start();
    }

    private void restoreSelection() {
        if (model == null || usersTable == null) {
            return;
        }

        User selected = model.getSelectedUser();
        if (selected == null || selected.getId() == null) {
            return;
        }

        for (User user : model.adminAndCoordinatorUsersView()) {
            if (user != null && selected.getId().equals(user.getId())) {
                usersTable.getSelectionModel().select(user);
                usersTable.scrollTo(user);
                updateActionState(user);
                return;
            }
        }

        usersTable.getSelectionModel().clearSelection();
        updateActionState(null);
    }

    private Optional<User> showUserDialog(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(ViewType.ADMIN_COORDINATOR_DIALOG.getFxmlPath())
            );
            Parent root = loader.load();

            AdminCoordinatorDialogController controller = loader.getController();
            controller.setUser(user == null ? null : user.copy());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            if (usersTable != null && usersTable.getScene() != null) {
                stage.initOwner(usersTable.getScene().getWindow());
            }
            stage.setTitle(user == null ? "Add User" : "Edit User");
            stage.setScene(new Scene(root));
            DialogUtils.configureHalfScreenDialogStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                return Optional.of(controller.getUser());
            }
        } catch (IOException e) {
            showMessageDialog("User Dialog", "Unable to open user dialog: " + e.getMessage());
        }
        return Optional.empty();
    }

    private record ErrorDialogDetails(String type, String error) {}

    private void showUserActionErrorDialog(String title, String message, Throwable throwable) {
        ErrorDialogDetails details = mapUserActionError(throwable);
        DialogUtils.showDetailedError(
                title,
                message,
                details.type(),
                details.error(),
                resolveOwnerWindow()
        );
    }

    private void showMessageDialog(String title, String message) {
        DialogUtils.showMessageDialog(title, message, "OK", resolveOwnerWindow());
    }

    private Window resolveOwnerWindow() {
        return usersTable != null && usersTable.getScene() != null
                ? usersTable.getScene().getWindow()
                : null;
    }

    private ErrorDialogDetails mapUserActionError(Throwable throwable) {
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
        } else if (containsAny(normalized, "required", "valid", "must", "cannot", "password", "email", "phone", "last active admin", "only coordinator")) {
            type = "Validation";
            detail = "The request violates a validation or business rule.";
        } else if (containsAny(normalized, "not found", "no longer exists", "missing")) {
            type = "User Not Found";
            detail = "The selected user record no longer exists.";
        } else if (root instanceof SQLException) {
            type = "Database Error";
            detail = "The database could not process this request.";
        } else {
            type = "Unexpected Error";
            detail = "Something unexpected happened while processing this request.";
        }

        boolean preferTechnical = containsAny(normalized,
                "required", "valid", "must", "cannot", "password", "email", "phone",
                "last active admin", "only coordinator", "permission", "denied", "forbidden",
                "not authorized", "unauthorized", "not found", "no longer exists", "missing",
                "duplicate", "unique", "constraint", "already exists", "violat");
        return new ErrorDialogDetails(type, chooseDialogDetail(detail, technicalMessage, preferTechnical));
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

    private String chooseDialogDetail(String friendlyMessage, String technicalMessage, boolean preferTechnical) {
        if (preferTechnical && technicalMessage != null && !technicalMessage.isBlank()) {
            return abbreviate(technicalMessage, 180);
        }
        return friendlyMessage;
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
        boolean canManage = canManageUsers();
        if (addButton != null) {
            addButton.setDisable(!canManage);
        }

        if (selected == null) {
            editButton.setDisable(true);
            deactivateButton.setDisable(true);
            deactivateButton.setText("Deactivate User");
            return;
        }

        boolean isDeleted = selected.isDeleted();
        editButton.setDisable(!canManage || isDeleted);
        deactivateButton.setDisable(!canManage);
        deactivateButton.setText(isDeleted ? "Restore User" : "Deactivate User");
    }

    private boolean canManageUsers() {
        return model != null && model.isAdmin();
    }

    private boolean isCurrentSessionUser(User user) {
        if (user == null || user.getId() == null || model == null || model.getCurrentUser() == null) {
            return false;
        }

        Long currentUserId = model.getCurrentUser().getId();
        return currentUserId != null && currentUserId.equals(user.getId());
    }

    private void forceLogout(String message) {
        Window owner = resolveOwnerWindow();
        DialogUtils.showMessageDialog("Session Updated", message, "OK", owner);
        SessionManager.clearCurrentUser();

        if (owner instanceof Stage stage) {
            try {
                SceneNavigator.openLogin(stage);
            } catch (IOException e) {
                showMessageDialog("Log Out", "Could not return to the login page: " + e.getMessage());
            }
        }
    }
}
