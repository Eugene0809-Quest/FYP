package com.questiu.scheduler.ui;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manual roster-building screen (supervisor-requested item #3: admin
 * manual/auto shift assignment toggle). Lets the admin pick who works each
 * shift slot themselves instead of running the CP-SAT solver - Section 3.7.
 *
 * Each slot's ComboBox is pre-filtered to employees who are role-matched
 * AND available for that exact shift (Availability.covers) - so the
 * ROLE_MATCH and AVAILABILITY hard constraints (Section 3.4) can never be
 * violated from this screen; they're enforced by construction rather than
 * by validation. NO_OVERLAP and MAX_HOURS depend on the *combination* of
 * picks across every slot, so they can't be filtered per-slot - those are
 * checked dynamically by ManualScheduleValidator instead and surfaced as
 * warnings here, without blocking submission (a human override channel is
 * the point of this screen).
 */
public class ManualAssignmentPane extends BorderPane {

    private static final String[] DAY_NAMES =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    public static class SlotRow {
        private final Shift shift;
        private final int slotNumber;
        private final ComboBox<Employee> assignBox;

        SlotRow(Shift shift, int slotNumber, List<Employee> eligible) {
            this.shift = shift;
            this.slotNumber = slotNumber;

            ObservableList<Employee> options = FXCollections.observableArrayList();
            options.add(null); // "-- Unassigned --"
            options.addAll(eligible);

            this.assignBox = new ComboBox<>(options);
            this.assignBox.setCellFactory(cb -> employeeCell());
            this.assignBox.setButtonCell(employeeCell());
            this.assignBox.getSelectionModel().selectFirst(); // starts unassigned
        }

        private ListCell<Employee> employeeCell() {
            return new ListCell<>() {
                @Override
                protected void updateItem(Employee e, boolean empty) {
                    super.updateItem(e, empty);
                    setText(empty ? null : (e == null ? "-- Unassigned --" : e.getFullName()));
                }
            };
        }

        public String getDay() { return DAY_NAMES[shift.getDayOfWeek() - 1]; }
        public String getTime() { return shift.getStartTime() + " - " + shift.getEndTime(); }
        public String getSlot() { return "Slot " + slotNumber + " of " + shift.getStaffNeeded(); }
    }

    private final TableView<SlotRow> table = new TableView<>();
    private final Label warningLabel = new Label();

    public ManualAssignmentPane(List<Employee> employees, List<Shift> shifts, List<Availability> availability) {
        Map<Integer, List<Availability>> availByEmployee = availability.stream()
                .collect(Collectors.groupingBy(Availability::getEmployeeId));

        ObservableList<SlotRow> rows = FXCollections.observableArrayList();
        for (Shift s : shifts) {
            List<Employee> eligible = employees.stream()
                    .filter(e -> e.getRoleId() == s.getRequiredRoleId())
                    .filter(e -> availByEmployee.getOrDefault(e.getEmployeeId(), List.of())
                            .stream().anyMatch(a -> a.covers(s)))
                    .collect(Collectors.toList());
            for (int slot = 1; slot <= s.getStaffNeeded(); slot++) {
                rows.add(new SlotRow(s, slot, eligible));
            }
        }

        TableColumn<SlotRow, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(new PropertyValueFactory<>("day"));
        TableColumn<SlotRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        TableColumn<SlotRow, String> slotCol = new TableColumn<>("Slot");
        slotCol.setCellValueFactory(new PropertyValueFactory<>("slot"));
        TableColumn<SlotRow, Void> assignCol = new TableColumn<>("Assign Employee");
        assignCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(getTableView().getItems().get(getIndex()).assignBox);
                }
            }
        });

        table.getColumns().addAll(dayCol, timeCol, slotCol, assignCol);
        table.setItems(rows);

        warningLabel.setStyle("-fx-text-fill: #b45309;");
        warningLabel.setWrapText(true);

        setCenter(table);
        setBottom(warningLabel);
        BorderPane.setMargin(warningLabel, new Insets(6, 0, 0, 0));
    }

    /** Builds RosterAssignment entries from whatever the admin has picked so far (skips unassigned slots). */
    public List<RosterAssignment> buildAssignments() {
        List<RosterAssignment> result = new ArrayList<>();
        for (SlotRow row : table.getItems()) {
            Employee e = row.assignBox.getValue();
            if (e != null) {
                result.add(new RosterAssignment(e.getEmployeeId(), row.shift.getShiftId()));
            }
        }
        return result;
    }

    public void setWarningText(String text) {
        warningLabel.setText(text);
    }
}
