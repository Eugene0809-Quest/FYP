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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Login screen (Section 3.7 - added per supervisor feedback requesting
 * admin login access control). Authenticates against user_account.
 *
 * UI polish pass: the form now sits as a white "card" (login-card style
 * class) centered over a soft gradient background (app-background), rather
 * than a plain VBox filling the whole window - purely visual, no change to
 * the authentication logic below.
 */
public class LoginView {

    public static StackPane build(Consumer<User> onLoginSuccess) {
        Label titleLabel = new Label("SmartShift");
        titleLabel.getStyleClass().add("login-title");

        Label subtitleLabel = new Label("Sign in to continue");
        subtitleLabel.getStyleClass().add("status-label");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(240);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(240);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(240);

        Button loginButton = new Button("Log In");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setMaxWidth(240);
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

        VBox card = new VBox(14, titleLabel, subtitleLabel, usernameField, passwordField, loginButton, errorLabel);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(320);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        StackPane background = new StackPane(card);
        background.getStyleClass().add("app-background");
        background.setPadding(new Insets(20));
        return background;
    }
}
