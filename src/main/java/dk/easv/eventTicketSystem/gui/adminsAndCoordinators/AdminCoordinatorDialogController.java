package dk.easv.eventTicketSystem.gui.adminsAndCoordinators;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.util.UserUiText;
import dk.easv.eventTicketSystem.util.UserValidationRules;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class AdminCoordinatorDialogController {
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

    private User user;
    private boolean saved;
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
        saved = false;
        close();
    }

    @FXML
    private void onSave() {
        if (!validateAll()) {
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

        saved = true;
        close();
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
        txtPassword.textProperty().addListener((obs, oldValue, newValue) -> validatePassword());
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
}
