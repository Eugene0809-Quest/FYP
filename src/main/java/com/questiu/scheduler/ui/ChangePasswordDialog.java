package com.questiu.scheduler.ui;

import com.questiu.scheduler.model.User;
import com.questiu.scheduler.service.AccountService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * "Change Password" for whoever is signed in (admin or staff). The current
 * password must be typed first, so someone who walks up to an unlocked screen
 * cannot lock the real owner out.
 */
public class ChangePasswordDialog {

    public static void show(Window owner, User user) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        dialog.setTitle("Change Password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(18));

        PasswordField currentField = new PasswordField();
        PasswordField newField = new PasswordField();
        newField.setPromptText("at least 8 characters");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("type the new password again");
        currentField.setPrefWidth(240);
        newField.setPrefWidth(240);
        confirmField.setPrefWidth(240);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        int r = 0;
        Label who = new Label("Signed in as: " + user.getUsername());
        who.setStyle("-fx-font-weight: bold;");
        grid.add(who, 0, r++, 2, 1);
        grid.addRow(r++, new Label("Current Password:"), currentField);
        grid.addRow(r++, new Label("New Password:"), newField);
        grid.addRow(r++, new Label("Confirm New Password:"), confirmField);
        grid.add(errorLabel, 1, r++);

        Button saveButton = new Button("Change Password");
        saveButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());
        saveButton.setOnAction(e -> {
            try {
                String error = new AccountService().changeOwnPassword(
                        user.getUsername(), currentField.getText(), newField.getText(), confirmField.getText());
                if (error != null) {
                    errorLabel.setText(error);
                    return;
                }
                dialog.close();
                Alert notice = new Alert(Alert.AlertType.INFORMATION);
                if (owner != null) notice.initOwner(owner);
                notice.setTitle("Password changed");
                notice.setHeaderText("Your password has been changed");
                notice.setContentText("Use the new password the next time you log in.");
                notice.show();
            } catch (Exception ex) {
                errorLabel.setText("Could not change the password: " + ex.getMessage());
            }
        });
        grid.add(new HBox(10, saveButton, cancelButton), 1, r);

        dialog.setScene(new Scene(grid, 470, 270));
        dialog.showAndWait();
    }
}
