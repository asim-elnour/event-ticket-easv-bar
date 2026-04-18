package dk.easv.eventTicketSystem.gui.adminsAndCoordinators;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.gui.model.AppModel;
import dk.easv.eventTicketSystem.util.DialogUtils;
import dk.easv.eventTicketSystem.util.UserUiText;
import dk.easv.eventTicketSystem.util.UserValidationRules;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.util.Locale;

public class AdminCoordinatorDialogController {
    @FXML private BorderPane dialogPane;
    @FXML private ScrollPane dialogScrollPane;
    @FXML private VBox loadingOverlay;
    @FXML private Button btnCancel;
    @FXML private Button btnSave;
    @FXML private TextField txtUsername;
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private ComboBox<Role> cmbRole;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtRepeatPassword;
    @FXML private Label errUsername;
    @FXML private Label errFirstName;
    @FXML private Label errLastName;
    @FXML private Label errEmail;
    @FXML private Label errPhone;
    @FXML private Label errRole;
    @FXML private Label errPassword;
    @FXML private Label errRepeatPassword;

    private AppModel model;
    private User user;
    private boolean saved;
    private boolean saving;
    private boolean isEdit;

    @FXML
    public void initialize() {
        cmbRole.getItems().setAll(Role.values());
        cmbRole.setConverter(new StringConverter<>() {
            @Override
            public String toString(Role role) {
                return UserUiText.roleLabel(role);
            }

            @Override
            public Role fromString(String string) {
                return null;
            }
        });
        setupLiveValidation();
        clearAllErrors();
        updateSavingState(false);
        installCloseGuard();
    }

    public void setModel(AppModel model) {
        this.model = model;
    }

    public void setUser(User user) {
        this.user = user;
        this.isEdit = user != null;
        if (isEdit) {
            txtUsername.setText(user.getUsername());
            txtFirstName.setText(user.getFirstName());
            txtLastName.setText(user.getLastName());
            txtEmail.setText(user.getEmail());
            txtPhone.setText(user.getPhone());
            cmbRole.setValue(user.getRole());
            txtPassword.clear();
            txtRepeatPassword.clear();
            txtPassword.setPromptText("Leave blank to keep existing password");
            txtRepeatPassword.setPromptText("Repeat new password");
        } else {
            txtUsername.clear();
            txtFirstName.clear();
            txtLastName.clear();
            txtEmail.clear();
            txtPhone.clear();
            cmbRole.setValue(Role.COORDINATOR);
            txtPassword.clear();
            txtRepeatPassword.clear();
            txtPassword.setPromptText("Password");
            txtRepeatPassword.setPromptText("Repeat password");
        }
        clearAllErrors();
    }

    public User getUser() {
        return user;
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void onCancel() {
        if (saving) {
            return;
        }
        saved = false;
        close();
    }

    @FXML
    private void onSave() {
        if (saving) {
            return;
        }
        if (!validateAll()) {
            return;
        }
        if (model == null) {
            DialogUtils.showError("User Dialog", null, "User dialog is missing the application model.");
            return;
        }

        String username = txtUsername.getText().trim();
        String firstName = txtFirstName.getText().trim();
        String lastName = txtLastName.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();
        String password = UserValidationRules.normalizeRequired(txtPassword.getText());
        Role selectedRole = cmbRole.getValue();

        if (isEdit) {
            user.setUsername(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setPhone(phone);
            user.setRole(selectedRole);
            if (!password.isEmpty()) {
                user.setPassword(password);
            }
        } else {
            user = new User(username, firstName, lastName, email, password, selectedRole);
            user.setPhone(phone);
            user.setDeleted(false);
            user.setEventCoordinatorRemoved(false);
        }

        persistUser();
    }

    private void close() {
        Stage stage = (Stage) txtUsername.getScene().getWindow();
        stage.close();
    }

    private void setupLiveValidation() {
        txtUsername.textProperty().addListener((obs, oldValue, newValue) -> validateUsername());
        txtFirstName.textProperty().addListener((obs, oldValue, newValue) -> validateFirstName());
        txtLastName.textProperty().addListener((obs, oldValue, newValue) -> validateLastName());
        txtEmail.textProperty().addListener((obs, oldValue, newValue) -> validateEmail());
        txtPhone.textProperty().addListener((obs, oldValue, newValue) -> validatePhone());
        cmbRole.valueProperty().addListener((obs, oldValue, newValue) -> validateRole());
        txtPassword.textProperty().addListener((obs, oldValue, newValue) -> {
            validatePassword();
            validateRepeatPassword();
        });
        txtRepeatPassword.textProperty().addListener((obs, oldValue, newValue) -> validateRepeatPassword());
    }

    private boolean validateAll() {
        boolean ok = true;
        ok = validateUsername() && ok;
        ok = validateFirstName() && ok;
        ok = validateLastName() && ok;
        ok = validateEmail() && ok;
        ok = validatePhone() && ok;
        ok = validateRole() && ok;
        ok = validatePassword() && ok;
        ok = validateRepeatPassword() && ok;
        return ok;
    }

    private boolean validateUsername() {
        return validateRequiredAndMax(txtUsername, errUsername, "Username");
    }

    private boolean validateFirstName() {
        return validateRequiredAndMax(txtFirstName, errFirstName, "First name");
    }

    private boolean validateLastName() {
        return validateRequiredAndMax(txtLastName, errLastName, "Last name");
    }

    private boolean validatePhone() {
        String value = UserValidationRules.normalizeRequired(txtPhone.getText());
        if (value.isEmpty()) {
            showError(errPhone, "Phone is required.");
            return false;
        }
        if (value.length() > UserValidationRules.MAX_PHONE_LENGTH) {
            showError(errPhone, "Phone too long (max " + UserValidationRules.MAX_PHONE_LENGTH + ").");
            return false;
        }
        if (!UserValidationRules.isValidPhone(value)) {
            showError(errPhone, "Phone must contain only numbers.");
            return false;
        }
        clearError(errPhone);
        return true;
    }

    private boolean validateRequiredAndMax(TextField field, Label error, String fieldLabel) {
        String value = UserValidationRules.normalizeRequired(field.getText());
        if (value == null || value.isBlank()) {
            showError(error, fieldLabel + " is required.");
            return false;
        }
        if (value.length() > UserValidationRules.MAX_TEXT_LENGTH) {
            showError(error, fieldLabel + " too long (max " + UserValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }
        clearError(error);
        return true;
    }

    private boolean validateEmail() {
        String value = UserValidationRules.normalizeRequired(txtEmail.getText());
        if (value.isEmpty()) {
            showError(errEmail, "Email is required.");
            return false;
        }
        if (value.length() > UserValidationRules.MAX_TEXT_LENGTH) {
            showError(errEmail, "Email too long (max " + UserValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }
        if (!UserValidationRules.isValidEmail(value)) {
            showError(errEmail, "Enter a valid email address.");
            return false;
        }
        clearError(errEmail);
        return true;
    }

    private boolean validateRole() {
        if (cmbRole.getValue() == null) {
            showError(errRole, "Role is required.");
            return false;
        }
        clearError(errRole);
        return true;
    }

    private boolean validatePassword() {
        String value = UserValidationRules.normalizeRequired(txtPassword.getText());
        String repeatValue = UserValidationRules.normalizeRequired(txtRepeatPassword.getText());

        if (!isEdit && value.isBlank()) {
            showError(errPassword, "Password is required.");
            return false;
        }

        if (isEdit && value.isBlank() && repeatValue.isBlank()) {
            clearError(errPassword);
            return true;
        }

        if (value.isBlank()) {
            showError(errPassword, "Enter a password first.");
            return false;
        }

        if (value.length() > UserValidationRules.MAX_TEXT_LENGTH) {
            showError(errPassword, "Password too long (max " + UserValidationRules.MAX_TEXT_LENGTH + ").");
            return false;
        }

        if (!UserValidationRules.isValidPassword(value)) {
            showError(errPassword, "Password must be at least " + UserValidationRules.MIN_PASSWORD_LENGTH + " characters.");
            return false;
        }

        clearError(errPassword);
        return true;
    }

    private boolean validateRepeatPassword() {
        String value = UserValidationRules.normalizeRequired(txtPassword.getText());
        String repeatValue = UserValidationRules.normalizeRequired(txtRepeatPassword.getText());

        if (isEdit && value.isBlank() && repeatValue.isBlank()) {
            clearError(errRepeatPassword);
            return true;
        }

        if (repeatValue.isBlank()) {
            showError(errRepeatPassword, "Repeat password is required.");
            return false;
        }

        if (value.isBlank()) {
            showError(errRepeatPassword, "Enter the password first.");
            return false;
        }

        if (!value.equals(repeatValue)) {
            showError(errRepeatPassword, "Passwords do not match.");
            return false;
        }

        clearError(errRepeatPassword);
        return true;
    }

    private void showError(Label errorLabel, String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void clearError(Label errorLabel) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText("");
        errorLabel.setManaged(true);
        errorLabel.setVisible(false);
    }

    private void clearAllErrors() {
        clearError(errUsername);
        clearError(errFirstName);
        clearError(errLastName);
        clearError(errEmail);
        clearError(errPhone);
        clearError(errRole);
        clearError(errPassword);
        clearError(errRepeatPassword);
    }

    private void persistUser() {
        updateSavingState(true);

        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                if (isEdit) {
                    model.updateAdminOrCoordinator(user);
                    return user;
                }
                return model.addAdminOrCoordinator(user);
            }
        };

        task.setOnSucceeded(event -> {
            User persistedUser = task.getValue();
            if (persistedUser != null) {
                user = persistedUser;
            }
            saved = true;
            updateSavingState(false);
            close();
        });

        task.setOnFailed(event -> {
            updateSavingState(false);
            showUserActionErrorDialog(
                    isEdit ? "Edit User Failed" : "Add User Failed",
                    isEdit
                            ? "We couldn't save changes for this user."
                            : "We couldn't add this user right now.",
                    task.getException()
            );
        });

        Thread thread = new Thread(task, isEdit ? "edit-user-task" : "add-user-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSavingState(boolean saving) {
        this.saving = saving;
        if (dialogPane != null) {
            dialogPane.setDisable(saving);
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(saving);
            loadingOverlay.setManaged(saving);
        }
        if (btnSave != null) {
            btnSave.setDisable(saving);
        }
        if (btnCancel != null) {
            btnCancel.setDisable(saving);
        }
        if (txtUsername != null && txtUsername.getScene() != null) {
            txtUsername.getScene().setCursor(saving ? Cursor.WAIT : Cursor.DEFAULT);
        } else if (dialogScrollPane != null) {
            dialogScrollPane.setCursor(saving ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }

    private void installCloseGuard() {
        txtUsername.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }

            if (newScene.getWindow() instanceof Stage stage) {
                stage.setOnCloseRequest(event -> {
                    if (saving) {
                        event.consume();
                    }
                });
            }

            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    stage.setOnCloseRequest(event -> {
                        if (saving) {
                            event.consume();
                        }
                    });
                }
            });
        });
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

    private Window resolveOwnerWindow() {
        return txtUsername != null && txtUsername.getScene() != null
                ? txtUsername.getScene().getWindow()
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

        boolean preferTechnical = throwable instanceof UserException
                || containsAny(normalized,
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
}
