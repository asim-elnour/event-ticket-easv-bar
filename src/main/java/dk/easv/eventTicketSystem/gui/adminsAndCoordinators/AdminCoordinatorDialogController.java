package dk.easv.eventTicketSystem.gui.adminsAndCoordinators;

import dk.easv.eventTicketSystem.be.Role;
import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.util.UserUiText;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class AdminCoordinatorDialogController {
    private static final int MAX_TEXT_LENGTH = 255;

    @FXML private TextField txtUsername;
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private ComboBox<Role> cmbRole;
    @FXML private PasswordField txtPassword;
    @FXML private Label errUsername;
    @FXML private Label errFirstName;
    @FXML private Label errLastName;
    @FXML private Label errEmail;
    @FXML private Label errPhone;
    @FXML private Label errRole;
    @FXML private Label errPassword;

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
            txtPassword.setPromptText("Leave blank to keep existing password");
        } else {
            txtUsername.clear();
            txtFirstName.clear();
            txtLastName.clear();
            txtEmail.clear();
            txtPhone.clear();
            cmbRole.setValue(Role.COORDINATOR);
            txtPassword.clear();
            txtPassword.setPromptText("Password");
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
        String password = txtPassword.getText().trim();
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
        return validateRequiredAndMax(txtPhone, errPhone, "Phone");
    }

    private boolean validateRequiredAndMax(TextField field, Label error, String fieldLabel) {
        String value = field.getText();
        if (value == null || value.isBlank()) {
            showError(error, fieldLabel + " is required.");
            return false;
        }
        if (value.length() > MAX_TEXT_LENGTH) {
            showError(error, fieldLabel + " too long (max " + MAX_TEXT_LENGTH + ").");
            return false;
        }
        clearError(error);
        return true;
    }

    private boolean validateEmail() {
        return validateRequiredAndMax(txtEmail, errEmail, "Email");
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
        String value = txtPassword.getText();
        if (!isEdit && (value == null || value.isBlank())) {
            showError(errPassword, "Password is required.");
            return false;
        }
        clearError(errPassword);
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
    }
}
