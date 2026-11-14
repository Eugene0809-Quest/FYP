package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.JobPositionDao;
import com.questiu.scheduler.dao.RoleDao;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.JobPosition;
import com.questiu.scheduler.model.Role;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Edit Employee" (FYP2). Changes an existing employee's details: name, job
 * position, IC / EPF / SOCSO / EIS numbers, date joined, bank account,
 * employment type, hourly rate and weekly hour cap.
 *
 * The department is shown but locked: shifts, availability and rosters depend
 * on it. Someone who really moves department is deactivated and registered
 * again. Activating / deactivating is a separate action in the Employees tab.
 */
public class EditEmployeeDialog {

    public static void show(Stage owner, int employeeId, Runnable onSaved) {
        Employee current;
        String departmentName;
        List<JobPosition> positionsInDepartment;
        try {
            current = new EmployeeDao().findById(employeeId);
            if (current == null) {
                error(owner, "That employee could not be found. Click Refresh and try again.");
                return;
            }
            departmentName = new RoleDao().findAll().stream()
                    .filter(r -> r.getRoleId() == current.getRoleId())
                    .map(Role::getRoleName).findFirst().orElse("Department #" + current.getRoleId());
            positionsInDepartment = new JobPositionDao().findAll().stream()
                    .filter(p -> p.getRoleId() == current.getRoleId())
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            error(owner, "Could not load the employee: " + ex.getMessage());
            return;
        }
        final Employee emp = current;

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Edit Employee");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        TextField nameField = new TextField(emp.getFullName());
        Label departmentLabel = new Label(departmentName + "  (locked)");
        ComboBox<JobPosition> positionBox = new ComboBox<>(FXCollections.observableArrayList(positionsInDepartment));
        positionsInDepartment.stream()
                .filter(p -> emp.getJobPositionId() != null && p.getJobPositionId() == emp.getJobPositionId())
                .findFirst().ifPresent(p -> positionBox.getSelectionModel().select(p));

        TextField icField = new TextField(orEmpty(emp.getIcNumber()));
        TextField epfField = new TextField(orEmpty(emp.getEpfNumber()));
        TextField socsoField = new TextField(orEmpty(emp.getSocsoNumber()));
        TextField eisField = new TextField(orEmpty(emp.getEisNumber()));
        DatePicker joinedPicker = new DatePicker(emp.getDateJoined());
        joinedPicker.setConverter(DateFormats.dayMonthYear());
        TextField bankField = new TextField(orEmpty(emp.getBankAccountNumber()));
        ComboBox<EmploymentType> typeBox = new ComboBox<>(FXCollections.observableArrayList(EmploymentType.values()));
        typeBox.getSelectionModel().select(emp.getEmploymentType());
        TextField rateField = new TextField(emp.getHourlyRate().toPlainString());
        TextField maxHoursField = new TextField(emp.getMaxWeeklyHours().toPlainString());
        // Changing the type suggests that type's usual cap; the admin can still overwrite it.
        typeBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) maxHoursField.setText(newV.defaultMaxWeeklyHours().toString());
        });

        Label note = new Label("Changes apply to rosters and payroll made from now on. "
                + "Rosters and payroll already saved keep their old figures.");
        note.setWrapText(true);
        note.setMaxWidth(380);
        note.setStyle("-fx-text-fill: #64748b;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(280);

        int r = 0;
        grid.addRow(r++, new Label("Full Name:"), nameField);
        grid.addRow(r++, new Label("Department:"), departmentLabel);
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
        grid.add(note, 0, r++, 2, 1);
        grid.add(errorLabel, 1, r++);

        Button saveButton = new Button("Save Changes");
        saveButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());
        saveButton.setOnAction(e -> {
            try {
                String name = nameField.getText().trim();
                JobPosition position = positionBox.getValue();
                String ic = icField.getText().trim();
                EmploymentType type = typeBox.getValue();

                if (name.isEmpty() || type == null) {
                    errorLabel.setText("Full name and employment type are required.");
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

                Employee changed = new Employee(emp.getEmployeeId(), name, emp.getRoleId(), rate, maxHours,
                        emp.isActive(), blankToNull(bankField.getText()), type,
                        position == null ? null : position.getJobPositionId(),
                        ic, blankToNull(epfField.getText()), blankToNull(socsoField.getText()),
                        blankToNull(eisField.getText()), joinedPicker.getValue());
                new EmployeeDao().update(changed);
                dialog.close();
                if (onSaved != null) onSaved.run();
            } catch (SQLIntegrityConstraintViolationException dup) {
                String msg = dup.getMessage() == null ? "" : dup.getMessage();
                errorLabel.setText(msg.contains("uq_employee_ic") || msg.contains("ic_number")
                        ? "That IC number is already registered to another employee."
                        : "Could not save (nothing was changed): " + msg);
            } catch (Exception ex) {
                errorLabel.setText("Could not save (nothing was changed): " + ex.getMessage());
            }
        });
        grid.add(new HBox(10, saveButton, cancelButton), 1, r);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        dialog.setScene(new Scene(scroll, 540, 700));
        dialog.showAndWait();
    }

    private static void error(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(owner);
        alert.show();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
