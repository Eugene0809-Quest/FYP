package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.PreferenceDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Preference;
import com.questiu.scheduler.model.Shift;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Modal "Set Preferences" form. Lets the admin record, per shift the employee's
 * role can work, whether the employee would like to avoid or prefer it - the
 * data behind the solver's PreferenceViolations objective term (see
 * SchedulingEngine, "5. Preference violations").
 *
 * A preference is a soft request only: the solver uses it to choose between
 * employees who could all legitimately work a shift. It never overrides
 * availability, role match, coverage, overlap or the weekly hour cap.
 * "Neutral" is the default and is not stored (no row = neutral).
 */
public class PreferenceEditDialog {

    private static final String[] DAY_LABELS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    /** Dropdown index i means preference level (i - 2), i.e. -2 .. +2. */
    private static final String[] LEVEL_LABELS =
            {"Strongly avoid", "Avoid", "Neutral", "Prefer", "Strongly prefer"};

    public static void show(Stage owner, int employeeId, String employeeName, Runnable onSaved) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Shift Preferences - " + employeeName);

        List<Shift> shifts = new ArrayList<>();
        Map<Integer, Integer> currentLevels = new HashMap<>();
        try {
            Employee employee = new EmployeeDao().findAll().stream()
                    .filter(e -> e.getEmployeeId() == employeeId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Employee #" + employeeId + " not found"));
            int roleId = employee.getRoleId();
            shifts = new ShiftDao().findAll().stream()
                    .filter(s -> s.getRequiredRoleId() == roleId)
                    .sorted(Comparator.comparingInt(Shift::getDayOfWeek).thenComparing(Shift::getStartTime))
                    .collect(Collectors.toList());
            for (Preference p : new PreferenceDao().findByEmployeeId(employeeId)) {
                currentLevels.put(p.getShiftId(), p.getLevel());
            }
        } catch (Exception ex) {
            dialog.setScene(new Scene(
                    new VBox(new Label("Error loading preferences: " + ex.getMessage())), 420, 100));
            dialog.showAndWait();
            return;
        }
        final List<Shift> roleShifts = shifts;

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Day"), new Label("Shift time"), new Label("Preference"));

        List<ComboBox<String>> boxes = new ArrayList<>();
        for (int i = 0; i < roleShifts.size(); i++) {
            Shift s = roleShifts.get(i);
            ComboBox<String> box = new ComboBox<>();
            box.getItems().addAll(LEVEL_LABELS);
            int level = currentLevels.getOrDefault(s.getShiftId(), 0);
            box.getSelectionModel().select(Math.max(0, Math.min(4, level + 2)));
            boxes.add(box);
            grid.addRow(i + 1,
                    new Label(DAY_LABELS[s.getDayOfWeek() - 1]),
                    new Label(s.getStartTime() + " - " + s.getEndTime()),
                    box);
        }

        Label note = new Label(roleShifts.isEmpty()
                ? "No shifts are defined for this employee's role."
                : "A preference is a soft request: the solver uses it to choose between employees who "
                + "could all work a shift. It never overrides availability, coverage or hour limits. "
                + "Neutral means no preference.");
        note.setWrapText(true);
        note.setMaxWidth(440);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(440);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(340);

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());
        saveButton.setOnAction(e -> {
            List<Preference> toSave = new ArrayList<>();
            for (int i = 0; i < roleShifts.size(); i++) {
                int level = boxes.get(i).getSelectionModel().getSelectedIndex() - 2;
                if (level != 0) {
                    toSave.add(new Preference(employeeId, roleShifts.get(i).getShiftId(), level));
                }
            }
            try {
                new PreferenceDao().replaceForEmployee(employeeId, toSave);
                dialog.close();
                if (onSaved != null) onSaved.run();
            } catch (Exception ex) {
                errorLabel.setText("Error saving: " + ex.getMessage());
            }
        });

        VBox root = new VBox(10, note, scroll, errorLabel, new HBox(10, saveButton, cancelButton));
        root.setPadding(new Insets(12));
        dialog.setScene(new Scene(root, 480, 560));
        dialog.showAndWait();
    }
}
