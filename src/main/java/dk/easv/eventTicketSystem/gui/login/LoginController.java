package dk.easv.eventTicketSystem.gui.login;

import dk.easv.eventTicketSystem.be.User;
import dk.easv.eventTicketSystem.bll.UserLogic;
import dk.easv.eventTicketSystem.exceptions.UserException;
import dk.easv.eventTicketSystem.util.SceneNavigator;
import dk.easv.eventTicketSystem.util.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private BorderPane loginRoot;
    @FXML
    private Label loginTitle;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label errorLabel;

    private final UserLogic userLogic = new UserLogic();

    @FXML
    public void initialize() {
        loginTitle.setText("Login");
        emailField.setPromptText("Enter your email");
        passwordField.setPromptText("Enter your password");
        loginButton.setText("Login");
        emailField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> onLogin());
        updateAuthenticatingState(false);
    }

    @FXML
    private void onLogin() {
        String usernameOrEmail = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        if (usernameOrEmail.isBlank() || password.isBlank()) {
            errorLabel.setText("Email and password are required.");
            return;
        }

        if (loginRoot != null) {
            loginRoot.requestFocus();
        }
        emailField.deselect();
        passwordField.deselect();
        updateAuthenticatingState(true);
        errorLabel.setText("");

        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return userLogic.authenticate(usernameOrEmail, password);
            }
        };

        task.setOnSucceeded(event -> {
            updateAuthenticatingState(false);
            User user = task.getValue();
            if (user == null) {
                errorLabel.setText("Invalid credentials.");
                return;
            }

            SessionManager.setCurrentUser(user);
            try {
                openMainView();
            } catch (Exception e) {
                errorLabel.setText("Login failed: " + e.getMessage());
            }
        });

        task.setOnFailed(event -> {
            updateAuthenticatingState(false);
            Throwable throwable = task.getException();
            if (throwable instanceof UserException) {
                String exceptionMessage = throwable.getMessage();
                if ("Invalid username or password.".equals(exceptionMessage)) {
                    errorLabel.setText("Invalid credentials.");
                } else {
                    errorLabel.setText("Login failed: " + exceptionMessage);
                }
                return;
            }
            String message = throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                    ? "Unknown error"
                    : throwable.getMessage();
            errorLabel.setText("Login failed: " + message);
        });

        Thread thread = new Thread(task, "login-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void openMainView() throws Exception {
        Stage stage = (Stage) emailField.getScene().getWindow();
        SceneNavigator.openMain(stage);
    }

    private void updateAuthenticatingState(boolean authenticating) {
        if (emailField != null) {
            emailField.setDisable(authenticating);
        }
        if (passwordField != null) {
            passwordField.setDisable(authenticating);
        }
        if (loginButton != null) {
            loginButton.setDisable(authenticating);
        }
        if (loginRoot != null) {
            loginRoot.setCursor(authenticating ? Cursor.WAIT : Cursor.DEFAULT);
        }
        if (emailField != null && emailField.getScene() != null) {
            emailField.getScene().setCursor(authenticating ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }
}
