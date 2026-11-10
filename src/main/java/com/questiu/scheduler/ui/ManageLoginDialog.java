package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.UserDao;
import com.questiu.scheduler.model.User;
import com.questiu.scheduler.service.AccountService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Admin tool for one employee's login. If the employee already has a login it
 * lets the admin set a new password (a forgotten password). If the employee was
 * registered without a login it lets the admin create one.
 */
public class ManageLoginDialog {

    public static void show(Window owner, int employeeId, String employeeName, Runnable onDone) {
        User existing;
        try {
            existing = new UserDao().findByEmployeeId(employeeId);
        } catch (Exception ex) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not look up the login: " + ex.getMessage());
            if (owner != null) error.initOwner(owner);
            error.show();
            return;
        }
        final boolean hasLogin = existing != null;

        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        dialog.setTitle(hasLogin ? "Reset Password" : "Create Login");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(18));

        Label name = new Label(employeeName);
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label info = new Label(hasLogin
                ? "This employee already has a login. Set a new password, for example when they forgot theirs."
                : "This employee has no login yet. Create one so they can sign in.");
        info.setWrapText(true);
        info.setMaxWidth(380);

        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g. ahmad");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("at least 8 characters");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("type the password again");
        usernameField.setPrefWidth(240);
        passwordField.setPrefWidth(240);
        confirmField.setPrefWidth(240);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        int r = 0;
        grid.add(name, 0, r++, 2, 1);
        grid.add(info, 0, r++, 2, 1);
        if (hasLogin) {
            grid.addRow(r++, new Label("Username:"), new Label(existing.getUsername()));
        } else {
            grid.addRow(r++, new Label("Username:"), usernameField);
        }
        grid.addRow(r++, new Label(hasLogin ? "New Password:" : "Initial Password:"), passwordField);
        grid.addRow(r++, new Label("Confirm Password:"), confirmField);
        grid.add(errorLabel, 1, r++);

        final String existingUsername = hasLogin ? existing.getUsername() : null;
        Button saveButton = new Button(hasLogin ? "Reset Password" : "Create Login");
        saveButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());
        saveButton.setOnAction(e -> {
            try {
                AccountService service = new AccountService();
                String error = hasLogin
                        ? service.resetPassword(employeeId, passwordField.getText(), confirmField.getText())
                        : service.createLogin(employeeId, employeeName, usernameField.getText(),
                                passwordField.getText(), confirmField.getText());
                if (error != null) {
                    errorLabel.setText(error);
                    return;
                }
                String shownUsername = hasLogin ? existingUsername : usernameField.getText().trim().toLowerCase();
                dialog.close();
                if (onDone != null) onDone.run();
                Alert notice = new Alert(Alert.AlertType.INFORMATION);
                if (owner != null) notice.initOwner(owner);
                notice.setTitle(hasLogin ? "Password reset" : "Login created");
                notice.setHeaderText(employeeName);
                notice.setContentText("Username: " + shownUsername + "\n\n"
                        + "Give the employee this username and the password you typed. "
                        + "The password is not shown again.");
                notice.show();
            } catch (Exception ex) {
                errorLabel.setText("Could not save (nothing was changed): " + ex.getMessage());
            }
        });
        grid.add(new HBox(10, saveButton, cancelButton), 1, r);

        dialog.setScene(new Scene(grid, 480, hasLogin ? 300 : 320));
        dialog.showAndWait();
    }
}
