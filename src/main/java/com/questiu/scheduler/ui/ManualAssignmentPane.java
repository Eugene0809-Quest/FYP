package com.questiu.scheduler.ui;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * The slots are split into one inner sub-tab per role (e.g. "Front-of-House",
 * "Kitchen") rather than one long scrolling table, since with several roles
 * and several shifts per day the flat table ran to 40+ rows and it wasn't
 * obvious where one role's shifts ended and the next began.
 */
public class ManualAssignmentPane extends BorderPane {

    private static final String[] DAY_NAMES =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    public static class SlotRow {
        private final Shift shift;
        private final int slotNumber;
        private final ComboBox<Employee> assignBox;
        private final List<Employee> eligible;              // role + availability match, before leave
        private final ObservableList<Employee> options;     // what the dropdown currently shows
        private final StringProperty dateText = new SimpleStringProperty("");

        SlotRow(Shift shift, int slotNumber, List<Employee> eligible) {
            this.shift = shift;
            this.slotNumber = slotNumber;
            this.eligible = eligible;

            options = FXCollections.observableArrayList();
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

        /**
         * Rebuilds this slot's dropdown without the people on approved leave that
         * day. A pick that is still allowed is kept. A pick that is now on leave is
         * cleared and described in the returned text; returns null if nothing was cleared.
         */
        String applyLeave(Map<Integer, Set<Integer>> leaveDays, LocalDate weekStart) {
            Employee previous = assignBox.getValue();
            List<Employee> allowed = eligible.stream()
                    .filter(e -> !leaveDays.getOrDefault(e.getEmployeeId(), Set.of())
                            .contains(shift.getDayOfWeek()))
                    .collect(Collectors.toList());

            List<Employee> newOptions = new ArrayList<>();
            newOptions.add(null); // "-- Unassigned --"
            newOptions.addAll(allowed);
            options.setAll(newOptions);

            if (previous != null && allowed.contains(previous)) {
                assignBox.getSelectionModel().select(previous);
                return null;
            }
            // Back to unassigned. The value must be cleared explicitly: after the item list is
            // replaced, ComboBox keeps the old value and selectFirst() alone would not drop it.
            assignBox.setValue(null);
            assignBox.getSelectionModel().selectFirst();
            if (previous == null) {
                return null;
            }
            LocalDate date = weekStart.plusDays(shift.getDayOfWeek() - 1L);
            return String.format("%s removed from %s %s %s - %s (approved leave)",
                    previous.getFullName(), getDay(),
                    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    shift.getStartTime(), shift.getEndTime());
        }

        /** Calendar date of this slot for the chosen week, e.g. "04/11/2026" or "05/11/2026 (Holiday)". */
        public String getDateText() { return dateText.get(); }
        public StringProperty dateTextProperty() { return dateText; }

        public String getDay() { return DAY_NAMES[shift.getDayOfWeek() - 1]; }
        public String getTime() { return shift.getStartTime() + " - " + shift.getEndTime(); }
        public String getSlot() { return "Slot " + slotNumber + " of " + shift.getStaffNeeded(); }
    }

    private final List<SlotRow> allRows = new ArrayList<>();
    private final Label warningLabel = new Label();

    /**
     * @param roleNamesById role_id -> role_name (e.g. from RoleDao), used only
     *                      for the sub-tab labels; falls back to "Role #N" for
     *                      any role_id not present in the map.
     */
    public ManualAssignmentPane(List<Employee> employees, List<Shift> shifts, List<Availability> availability,
                                 Map<Integer, String> roleNamesById) {
        Map<Integer, List<Availability>> availByEmployee = availability.stream()
                .collect(Collectors.groupingBy(Availability::getEmployeeId));

        // Build every slot row first (unchanged logic), then split them by role for display.
        for (Shift s : shifts) {
            List<Employee> eligible = employees.stream()
                    .filter(e -> e.getRoleId() == s.getRequiredRoleId())
                    .filter(e -> availByEmployee.getOrDefault(e.getEmployeeId(), List.of())
                            .stream().anyMatch(a -> a.covers(s)))
                    .collect(Collectors.toList());
            for (int slot = 1; slot <= s.getStaffNeeded(); slot++) {
                allRows.add(new SlotRow(s, slot, eligible));
            }
        }

        Map<Integer, List<SlotRow>> rowsByRole = new LinkedHashMap<>();
        for (SlotRow row : allRows) {
            rowsByRole.computeIfAbsent(row.shift.getRequiredRoleId(), k -> new ArrayList<>()).add(row);
        }

        TabPane roleTabs = new TabPane();
        roleTabs.getTabs().forEach(t -> t.setClosable(false));
        rowsByRole.entrySet().stream()
                // Sort tabs alphabetically by role name so the order is stable and predictable
                // across app restarts, rather than depending on database row order.
                .sorted((a, b) -> roleNamesById.getOrDefault(a.getKey(), "Role #" + a.getKey())
                        .compareToIgnoreCase(roleNamesById.getOrDefault(b.getKey(), "Role #" + b.getKey())))
                .forEach(entry -> {
                    String roleName = roleNamesById.getOrDefault(entry.getKey(), "Role #" + entry.getKey());
                    Tab tab = new Tab(roleName, buildTable(entry.getValue()));
                    tab.setClosable(false);
                    roleTabs.getTabs().add(tab);
                });

        warningLabel.setStyle("-fx-text-fill: #b45309;");
        warningLabel.setWrapText(true);

        setCenter(roleTabs);
        setBottom(warningLabel);
        BorderPane.setMargin(warningLabel, new Insets(6, 0, 0, 0));
    }

    private TableView<SlotRow> buildTable(List<SlotRow> rowsForRole) {
        TableView<SlotRow> table = new TableView<>();

        TableColumn<SlotRow, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("dateText"));
        dateCol.setPrefWidth(175);
        TableColumn<SlotRow, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(new PropertyValueFactory<>("day"));
        dayCol.setPrefWidth(100);
        TableColumn<SlotRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        timeCol.setPrefWidth(125);
        TableColumn<SlotRow, String> slotCol = new TableColumn<>("Slot");
        slotCol.setCellValueFactory(new PropertyValueFactory<>("slot"));
        slotCol.setPrefWidth(105);
        TableColumn<SlotRow, Void> assignCol = new TableColumn<>("Assign Employee");
        assignCol.setPrefWidth(240);
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

        table.getColumns().addAll(dateCol, dayCol, timeCol, slotCol, assignCol);
        table.setItems(FXCollections.observableArrayList(rowsForRole));
        return table;
    }

    /** Builds RosterAssignment entries from whatever the admin has picked so far (skips unassigned slots), across every role tab. */
    public List<RosterAssignment> buildAssignments() {
        List<RosterAssignment> result = new ArrayList<>();
        for (SlotRow row : allRows) {
            Employee e = row.assignBox.getValue();
            if (e != null) {
                result.add(new RosterAssignment(e.getEmployeeId(), row.shift.getShiftId()));
            }
        }
        return result;
    }

    /**
     * Shows the real calendar date next to each weekday for the chosen roster week, and
     * marks public holidays (paid at 2x). Only the date text changes, so the admin's picks
     * are untouched when the week changes.
     *
     * @param weekStart the Monday of the roster week
     * @param holidays  dates stored in the public_holiday table
     */
    public void setWeek(LocalDate weekStart, Set<LocalDate> holidays) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (SlotRow row : allRows) {
            LocalDate date = weekStart.plusDays(row.shift.getDayOfWeek() - 1L);
            row.dateText.set(date.format(fmt) + (holidays.contains(date) ? " (Holiday)" : ""));
        }
    }

    /**
     * Hides people on approved leave from the dropdowns for the given roster week,
     * keeping every pick that is still valid (the screen is NOT rebuilt, so the
     * admin's other picks survive a change of week). Returns a description of each
     * pick that had to be cleared because that person is on leave.
     *
     * @param leaveDays employeeId -> days of the week (1 = Monday ... 7 = Sunday) on approved leave
     * @param weekStart the Monday of the roster week
     */
    public List<String> applyLeave(Map<Integer, Set<Integer>> leaveDays, LocalDate weekStart) {
        List<String> cleared = new ArrayList<>();
        for (SlotRow row : allRows) {
            String message = row.applyLeave(leaveDays, weekStart);
            if (message != null) cleared.add(message);
        }
        return cleared;
    }

    public void setWarningText(String text) {
        warningLabel.setText(text);
    }
}
