package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.UserDao;
import com.questiu.scheduler.model.User;
import com.questiu.scheduler.util.PasswordHasher;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Login screen (Section 3.7 - added per supervisor feedback requesting
 * admin login access control). Authenticates against user_account.
 */
public class LoginView {

    public static VBox build(Consumer<User> onLoginSuccess) {
        Label titleLabel = new Label("SmartShift - Sign In");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(220);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(220);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(240);

        Button loginButton = new Button("Log In");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Enter both username and password.");
                return;
            }
            try {
                User user = new UserDao().findByUsername(username);
                if (user == null || !PasswordHasher.matches(password, user.getPasswordHash())) {
                    errorLabel.setText("Invalid username or password.");
                    return;
                }
                errorLabel.setText("");
                onLoginSuccess.accept(user);
            } catch (Exception ex) {
                errorLabel.setText("Login error: " + ex.getMessage());
            }
        });

        VBox box = new VBox(12, titleLabel, usernameField, passwordField, loginButton, errorLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }
}
