package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.JobPositionDao;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.JobPosition;
import com.questiu.scheduler.model.Role;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modal "Register New Employee" form (Section 3.7 - data-entry screen).
 * Added per supervisor feedback on the FYP1 checkpoint: registration should
 * capture name, bank account, full/part-time (-> max hours), and position.
 *
 * FYP2: also captures the job position (the list changes with the chosen
 * department, so a Head Chef can never be filed under Front-of-House),
 * the IC / EPF / SOCSO / EIS numbers and the joining date, following the
 * lecturer's feedback on the prototype.
 */
public class EmployeeRegistrationDialog {

    public static void show(Stage owner, List<Role> roles, Runnable onSaved) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Register New Employee");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        List<JobPosition> loaded;
        try {
            loaded = new JobPositionDao().findAll();
        } catch (Exception ex) {
            loaded = new ArrayList<>();
        }
        final List<JobPosition> positions = loaded;

        TextField nameField = new TextField();
        ComboBox<Role> roleBox = new ComboBox<>(FXCollections.observableArrayList(roles));
        if (!roles.isEmpty()) roleBox.getSelectionModel().selectFirst();

        ComboBox<JobPosition> positionBox = new ComboBox<>();
        Runnable refreshPositions = () -> {
            Role selected = roleBox.getValue();
            if (selected == null) {
                positionBox.getItems().clear();
                return;
            }
            List<JobPosition> forDepartment = positions.stream()
                    .filter(p -> p.getRoleId() == selected.getRoleId())
                    .collect(Collectors.toList());
            positionBox.setItems(FXCollections.observableArrayList(forDepartment));
            if (!forDepartment.isEmpty()) positionBox.getSelectionModel().selectFirst();
        };
        roleBox.valueProperty().addListener((obs, oldV, newV) -> refreshPositions.run());
        refreshPositions.run();

        TextField icField = new TextField();
        icField.setPromptText("Test data only - use an invented number");
        TextField epfField = new TextField();
        TextField socsoField = new TextField();
        TextField eisField = new TextField();
        DatePicker joinedPicker = new DatePicker(LocalDate.now());

        TextField bankField = new TextField();
        ComboBox<EmploymentType> typeBox = new ComboBox<>(
                FXCollections.observableArrayList(EmploymentType.values()));
        typeBox.getSelectionModel().select(EmploymentType.FULL_TIME);
        TextField rateField = new TextField();
        TextField maxHoursField = new TextField(EmploymentType.FULL_TIME.defaultMaxWeeklyHours().toString());

        // Picking a type pre-fills a sensible default max-hours; still editable by the manager.
        typeBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) maxHoursField.setText(newV.defaultMaxWeeklyHours().toString());
        });

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(280);

        int r = 0;
        grid.addRow(r++, new Label("Full Name:"), nameField);
        grid.addRow(r++, new Label("Department:"), roleBox);
        grid.addRow(r++, new Label("Job Position:"), positionBox);
        grid.addRow(r++, new Label("IC Number:"), icField);
        grid.addRow(r++, new Label("EPF Number:"), epfField);
        grid.addRow(r++, new Label("SOCSO Number:"), socsoField);
        grid.addRow(r++, new Label("EIS Number:"), eisField);
        grid.addRow(r++, new Label("Date Joined:"), joinedPicker);
        grid.addRow(r++, new Label("Bank Account No.:"), bankField);
        grid.addRow(r++, new Label("Employment Type:"), typeBox);
        grid.addRow(r++, new Label("Hourly Rate (RM):"), rateField);
        grid.addRow(r++, new Label("Max Hours / Week:"), maxHoursField);
        grid.add(errorLabel, 1, r++);

        Button saveButton = new Button("Register");
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());
        saveButton.setOnAction(e -> {
            try {
                String name = nameField.getText().trim();
                Role role = roleBox.getValue();
                JobPosition position = positionBox.getValue();
                String ic = icField.getText().trim();
                String bank = bankField.getText().trim();
                EmploymentType type = typeBox.getValue();

                if (name.isEmpty() || role == null || type == null) {
                    errorLabel.setText("Full name, department, and employment type are required.");
                    return;
                }
                if (position == null && !positionBox.getItems().isEmpty()) {
                    errorLabel.setText("Please choose a job position.");
                    return;
                }
                if (ic.isEmpty()) {
                    errorLabel.setText("IC number is required (use an invented number for test data).");
                    return;
                }

                BigDecimal rate;
                BigDecimal maxHours;
                try {
                    rate = new BigDecimal(rateField.getText().trim());
                    maxHours = new BigDecimal(maxHoursField.getText().trim());
                } catch (NumberFormatException nfe) {
                    errorLabel.setText("Hourly rate and max hours must be valid numbers.");
                    return;
                }
                if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                    errorLabel.setText("Hourly rate must be greater than 0.");
                    return;
                }
                if (maxHours.compareTo(BigDecimal.ZERO) <= 0) {
                    errorLabel.setText("Max hours per week must be greater than 0.");
                    return;
                }

                Employee newEmp = new Employee(0, name, role.getRoleId(), rate, maxHours, true, bank, type,
                        position == null ? null : position.getJobPositionId(),
                        ic, blankToNull(epfField.getText()), blankToNull(socsoField.getText()),
                        blankToNull(eisField.getText()), joinedPicker.getValue());
                new EmployeeDao().register(newEmp);
                dialog.close();
                if (onSaved != null) onSaved.run();
            } catch (SQLIntegrityConstraintViolationException dup) {
                String msg = dup.getMessage() == null ? "" : dup.getMessage();
                errorLabel.setText(msg.contains("uq_employee_ic") || msg.contains("ic_number")
                        ? "That IC number is already registered to another employee."
                        : "Error saving: " + msg);
            } catch (Exception ex) {
                errorLabel.setText("Error saving: " + ex.getMessage());
            }
        });

        HBox buttons = new HBox(10, saveButton, cancelButton);
        grid.add(buttons, 1, r);

        dialog.setScene(new Scene(grid, 460, 600));
        dialog.showAndWait();
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
