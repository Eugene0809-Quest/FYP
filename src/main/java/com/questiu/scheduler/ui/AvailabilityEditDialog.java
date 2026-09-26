package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.model.Availability;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal "Edit Availability" form (Section 3.7). Lets a manager set, per
 * day of week, whether an employee is available and for what time window
 * - the exact data the solver's availability hard constraint reads.
 * Added because the registration screen alone left every new employee
 * with zero availability rows, making them permanently unschedulable.
 */
public class AvailabilityEditDialog {

    private static final String[] DAY_LABELS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static void show(Stage owner, int employeeId, String employeeName, Runnable onSaved) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Edit Availability - " + employeeName);

        List<Availability> existing;
        try {
            existing = new AvailabilityDao().findByEmployeeId(employeeId);
        } catch (Exception ex) {
            existing = new ArrayList<>();
        }
        Map<Integer, Availability> byDay = new HashMap<>();
        for (Availability a : existing) byDay.put(a.getDayOfWeek(), a);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(""), new Label("Available"), new Label("Start (HH:mm)"), new Label("End (HH:mm)"));

        CheckBox[] checks = new CheckBox[7];
        TextField[] starts = new TextField[7];
        TextField[] ends = new TextField[7];

        for (int i = 0; i < 7; i++) {
            int dayNum = i + 1; // 1=Monday ... 7=Sunday
            Availability existingForDay = byDay.get(dayNum);

            CheckBox cb = new CheckBox();
            TextField startField = new TextField(
                    existingForDay != null ? existingForDay.getStartTime().format(TIME_FMT) : "11:00");
            TextField endField = new TextField(
                    existingForDay != null ? existingForDay.getEndTime().format(TIME_FMT) : "22:00");
            startField.setPrefWidth(70);
            endField.setPrefWidth(70);
            cb.setSelected(existingForDay != null);
            startField.disableProperty().bind(cb.selectedProperty().not());
            endField.disableProperty().bind(cb.selectedProperty().not());

            checks[i] = cb;
            starts[i] = startField;
            ends[i] = endField;

            grid.addRow(i + 1, new Label(DAY_LABELS[i]), cb, startField, endField);
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(320);
        grid.add(errorLabel, 0, 8, 4, 1);

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());

        saveButton.setOnAction(e -> {
            List<Availability> newRows = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                if (!checks[i].isSelected()) continue;
                int dayNum = i + 1;
                LocalTime start, end;
                try {
                    start = LocalTime.parse(starts[i].getText().trim(), TIME_FMT);
                    end = LocalTime.parse(ends[i].getText().trim(), TIME_FMT);
                } catch (DateTimeParseException pe) {
                    errorLabel.setText("Invalid time on " + DAY_LABELS[i] + " - use HH:mm, e.g. 11:00");
                    return;
                }
                if (!end.isAfter(start)) {
                    errorLabel.setText(DAY_LABELS[i] + ": end time must be after start time.");
                    return;
                }
                newRows.add(new Availability(employeeId, dayNum, start, end));
            }
            try {
                new AvailabilityDao().replaceForEmployee(employeeId, newRows);
                dialog.close();
                if (onSaved != null) onSaved.run();
            } catch (Exception ex) {
                errorLabel.setText("Error saving: " + ex.getMessage());
            }
        });

        HBox buttons = new HBox(10, saveButton, cancelButton);
        grid.add(buttons, 0, 9, 4, 1);

        dialog.setScene(new Scene(grid, 440, 400));
        dialog.showAndWait();
    }
}
