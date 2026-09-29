package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.PreferenceDao;
import com.questiu.scheduler.dao.PayrollRecordDao;
import com.questiu.scheduler.dao.PublicHolidayDao;
import com.questiu.scheduler.dao.ScheduleAssignmentDao;
import com.questiu.scheduler.dao.ScheduleDao;
import com.questiu.scheduler.dao.RoleDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.*;
import com.questiu.scheduler.payroll.PayrollCalculator;
import com.questiu.scheduler.solver.ManualScheduleValidator;
import com.questiu.scheduler.solver.PreferenceScorer;
import com.questiu.scheduler.solver.SchedulingEngine;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multi-tab prototype UI (Section 3.7). "Roster & Payroll" offers item #3
 * (Automatic/Manual toggle), item #4 (a "Week starting" picker feeding
 * public-holiday-aware payroll), and item #5 (EPF/SOCSO/EIS columns and net
 * pay - see PayrollCalculator/StatutoryRates for the formulas). "Employees"
 * is registration + listing. "Public Holidays" (item #4) lets the admin
 * maintain the calendar those payroll calls check against.
 */
public class MainApp extends Application {

    // --- Roster & Payroll tab ---
    private final TableView<PayrollRow> payrollTable = new TableView<>();
    private final Label statusLabel = new Label("Ready. Click 'Generate Roster & Payroll' to run.");
    private final RadioButton autoRadio = new RadioButton("Automatic (CP-SAT Solver)");
    private final RadioButton manualRadio = new RadioButton("Manual Assignment");
    private final DatePicker weekStartPicker = new DatePicker(mondayOf(LocalDate.now()));
    private final BorderPane rosterContent = new BorderPane();
    private List<Employee> cachedEmployees;
    private List<Shift> cachedShifts;
    private ManualAssignmentPane manualPane;
    private final Button saveButton = new Button("Save to Database");
    private User currentUser;

    // Result of the most recent Generate click (either mode), ready to persist on Save
    private List<RosterAssignment> lastAssignments;
    private List<PayrollRecord> lastPayroll;
    private Map<Integer, Shift> lastShiftsById;
    private LocalDate lastWeekStart;
    private String lastScheduleStatus; // 'GENERATED' (Automatic) or 'DRAFT' (Manual)

    // --- Employees tab ---
    private final TableView<EmployeeRow> employeeTable = new TableView<>();
    private final Label employeeStatusLabel = new Label("");
    private Map<Integer, String> roleNamesById = Map.of();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Constraint-Based Shift Scheduling and Payroll Optimisation (FYP1 Prototype)");
        showLoginScreen(stage);
        stage.show();
    }

    private void showLoginScreen(Stage stage) {
        stage.setScene(new Scene(LoginView.build(user -> showMainTabs(stage, user)), 400, 320));
    }

    private void showMainTabs(Stage stage, User loggedInUser) {
        currentUser = loggedInUser;
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Roster & Payroll", buildRosterTab()));
        tabs.getTabs().add(new Tab("Employees", buildEmployeesTab(stage)));
        tabs.getTabs().add(new Tab("Public Holidays", new PublicHolidayPane()));
        tabs.getTabs().forEach(t -> t.setClosable(false));

        Label sessionLabel = new Label(
                "Logged in as: " + loggedInUser.getFullName() + " (" + loggedInUser.getRole() + ")");
        Button logoutButton = new Button("Log Out");
        logoutButton.setOnAction(e -> showLoginScreen(stage));

        HBox sessionBar = new HBox(15, sessionLabel, logoutButton);
        sessionBar.setPadding(new Insets(6, 10, 6, 10));
        sessionBar.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setTop(sessionBar);
        root.setCenter(tabs);

        stage.setScene(new Scene(root, 1100, 650));
        stage.setMinWidth(950);
        stage.setMinHeight(500);
        loadEmployeesIntoTable();
    }

    // ---------------- Roster & Payroll tab ----------------

    /**
     * Item #3: an Automatic/Manual radio toggle. Automatic keeps running the
     * existing CP-SAT pipeline unchanged; Manual shows ManualAssignmentPane
     * instead and skips the solver entirely (computeManualPayroll). Both
     * paths end at the same PayrollCalculator call, so a manual roster and a
     * solved roster are always priced identically (Section 3.5 / 2.2.3).
     *
     * Item #4: the "Week starting" DatePicker tells both paths which Monday
     * shift_definition.day_of_week should be measured from, so shifts can be
     * checked against the public_holiday calendar for 2x holiday pay.
     */
    private BorderPane buildRosterTab() {
        ToggleGroup modeGroup = new ToggleGroup();
        autoRadio.setToggleGroup(modeGroup);
        manualRadio.setToggleGroup(modeGroup);
        autoRadio.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (manualRadio.isSelected()) {
                // Re-query on every switch to Manual, not just once when this tab first
                // opened - otherwise a new employee registered (or an availability edit
                // made) elsewhere in the app wouldn't show up here without restarting.
                loadShiftDataForRoster();
            } else {
                refreshRosterModeView();
            }
        });

        weekStartPicker.setPrefWidth(130);
        weekStartPicker.setTooltip(new Tooltip(
                "The Monday of the week you're generating a roster for - matched against the Public Holidays calendar."));

        Button generateButton = new Button("Generate Roster & Payroll");
        generateButton.setOnAction(e -> {
            if (manualRadio.isSelected()) {
                computeManualPayroll();
            } else {
                runAutoPipeline();
            }
        });

        saveButton.setDisable(true);
        saveButton.setTooltip(new Tooltip(
                "Persists the roster shown above into the schedule / schedule_assignment / payroll_record tables."));
        saveButton.setOnAction(e -> saveToDatabase());

        HBox controlsRow = new HBox(15, autoRadio, manualRadio,
                new Label("Week starting:"), weekStartPicker, generateButton, saveButton);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #444;");
        HBox statusRow = new HBox(statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        VBox topBar = new VBox(6, controlsRow, statusRow);
        topBar.setPadding(new Insets(10));

        setupPayrollTableColumns();
        rosterContent.setCenter(payrollTable);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(rosterContent);

        loadShiftDataForRoster();
        return root;
    }

    private void setupPayrollTableColumns() {
        TableColumn<PayrollRow, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<PayrollRow, String> hoursCol = new TableColumn<>("Total Hours");
        hoursCol.setCellValueFactory(new PropertyValueFactory<>("hours"));

        TableColumn<PayrollRow, String> otCol = new TableColumn<>("Overtime Hours");
        otCol.setCellValueFactory(new PropertyValueFactory<>("overtime"));

        TableColumn<PayrollRow, String> holidayCol = new TableColumn<>("Holiday Pay (RM)");
        holidayCol.setCellValueFactory(new PropertyValueFactory<>("holidayPay"));

        TableColumn<PayrollRow, String> payCol = new TableColumn<>("Gross Pay (RM)");
        payCol.setCellValueFactory(new PropertyValueFactory<>("pay"));

        TableColumn<PayrollRow, String> epfCol = new TableColumn<>("EPF (RM)");
        epfCol.setCellValueFactory(new PropertyValueFactory<>("epf"));

        TableColumn<PayrollRow, String> socsoCol = new TableColumn<>("SOCSO (RM)");
        socsoCol.setCellValueFactory(new PropertyValueFactory<>("socso"));

        TableColumn<PayrollRow, String> eisCol = new TableColumn<>("EIS (RM)");
        eisCol.setCellValueFactory(new PropertyValueFactory<>("eis"));

        TableColumn<PayrollRow, String> netPayCol = new TableColumn<>("Net Pay (RM)");
        netPayCol.setCellValueFactory(new PropertyValueFactory<>("netPay"));

        payrollTable.getColumns().addAll(nameCol, hoursCol, otCol, holidayCol, payCol, epfCol, socsoCol, eisCol, netPayCol);
    }

    /** Loads employees/shifts/availability/holidays fresh from MySQL - called once when this
     *  tab first opens, and again every time the admin switches to Manual mode (see the mode
     *  toggle listener above), so Manual mode never shows stale employee/availability data. */
    private void loadShiftDataForRoster() {
        Thread worker = new Thread(() -> {
            try {
                List<Employee> employees = new EmployeeDao().findAllActive();
                List<Shift> shifts = new ShiftDao().findAll();
                List<Availability> availability = new AvailabilityDao().findAll();
                Map<Integer, String> roleNames = new RoleDao().findAll().stream()
                        .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));
                javafx.application.Platform.runLater(() -> {
                    cachedEmployees = employees;
                    cachedShifts = shifts;
                    manualPane = new ManualAssignmentPane(employees, shifts, availability, roleNames);
                    refreshRosterModeView();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        statusLabel.setText("Error loading shift data: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void refreshRosterModeView() {
        if (manualRadio.isSelected()) {
            if (manualPane == null) {
                rosterContent.setCenter(new Label("Loading employees, shifts and availability..."));
            } else {
                SplitPane split = new SplitPane();
                split.setOrientation(Orientation.VERTICAL);
                split.getItems().setAll(manualPane, payrollTable);
                split.setDividerPositions(0.65);
                rosterContent.setCenter(split);
            }
            statusLabel.setText("Pick who works each shift, then click 'Generate Roster & Payroll'.");
        } else {
            rosterContent.setCenter(payrollTable);
            statusLabel.setText("Ready. Click 'Generate Roster & Payroll' to run the CP-SAT solver.");
        }
    }

    /** Snaps any picked date to the Monday of its week, so day_of_week=1 always lines up with weekStart itself. */
    private static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void runAutoPipeline() {
        LocalDate weekStart = mondayOf(weekStartPicker.getValue() != null ? weekStartPicker.getValue() : LocalDate.now());

        Thread worker = new Thread(() -> {
            try {
                System.out.println("[UI] Starting pipeline...");
                javafx.application.Platform.runLater(() -> statusLabel.setText("Loading data..."));

                List<Employee> employees = new EmployeeDao().findAllActive();
                System.out.println("[UI] Loaded " + employees.size() + " employees");
                List<Shift> shifts = new ShiftDao().findAll();
                System.out.println("[UI] Loaded " + shifts.size() + " shifts");
                List<Availability> availability = new AvailabilityDao().findAll();
                System.out.println("[UI] Loaded " + availability.size() + " availability rows");
                Set<LocalDate> publicHolidays = new PublicHolidayDao().findAllDates();
                System.out.println("[UI] Loaded " + publicHolidays.size() + " public holidays on file");

                javafx.application.Platform.runLater(() -> statusLabel.setText("Solving with CP-SAT..."));
                System.out.println("[UI] Calling SchedulingEngine.solve()...");
                List<Preference> preferences = new PreferenceDao().findAll();
                System.out.println("[UI] Loaded " + preferences.size() + " shift preferences");
                SchedulingEngine.SolveResult result = new SchedulingEngine()
                        .solve(employees, shifts, availability, preferences, 10.0);
                System.out.println("[UI] Solve returned. Status: " + result.status);

                if (!result.isFeasible()) {
                    javafx.application.Platform.runLater(() ->
                            statusLabel.setText("No feasible schedule found (status: " + result.status + ")"));
                    return;
                }

                Map<Integer, Shift> shiftsById = shifts.stream()
                        .collect(Collectors.toMap(Shift::getShiftId, s -> s));
                List<PayrollRecord> payroll = new PayrollCalculator()
                        .calculate(employees, shiftsById, result.assignments, weekStart, publicHolidays);
                System.out.println("[UI] Payroll calculated for " + payroll.size() + " employees");

                long holidaysThisWeek = publicHolidays.stream()
                        .filter(d -> !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7)))
                        .count();

                ObservableList<PayrollRow> rows = FXCollections.observableArrayList();
                for (PayrollRecord p : payroll) {
                    rows.add(new PayrollRow(
                            p.getEmployeeName(),
                            String.format("%.1f", p.getTotalHours()),
                            String.format("%.1f", p.getOvertimeHours()),
                            String.format("%.2f", p.getHolidayPay()),
                            String.format("%.2f", p.getTotalPay()),
                            String.format("%.2f", p.getStatutory().getEmployeeEpf()),
                            String.format("%.2f", p.getStatutory().getEmployeeSocso()),
                            String.format("%.2f", p.getStatutory().getEmployeeEis()),
                            String.format("%.2f", p.getStatutory().getNetPay())
                    ));
                }

                double totalNet = payroll.stream().mapToDouble(p -> p.getStatutory().getNetPay().doubleValue()).sum();
                double totalEmployerCost = payroll.stream().mapToDouble(p -> p.getStatutory().getEmployerTotalCost().doubleValue()).sum();
                PreferenceScorer.Result prefScore = PreferenceScorer.score(result.assignments, preferences);

                javafx.application.Platform.runLater(() -> {
                    payrollTable.setItems(rows);
                    statusLabel.setText(String.format(
                            "Status: %s | Solve time: %d ms | Unfilled slots: %d | Preferences granted: %d, avoided-but-assigned: %d (violation score %d) | Week: %s | Holidays this week: %d | Net payroll: RM%.2f | Employer cost: RM%.2f",
                            result.status, result.solveTimeMillis, result.totalUnfilled,
                            prefScore.preferredGranted, prefScore.avoidedImposed, prefScore.violationScore,
                            weekStart, holidaysThisWeek, totalNet, totalEmployerCost));
                    lastAssignments = result.assignments;
                    lastPayroll = payroll;
                    lastShiftsById = shiftsById;
                    lastWeekStart = weekStart;
                    lastScheduleStatus = "GENERATED";
                    saveButton.setDisable(false);
                });
                System.out.println("[UI] Done.");

            } catch (Throwable ex) {
                System.out.println("[UI] ERROR: " + ex);
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Manual-mode path for the Generate button: skips SchedulingEngine
     * entirely and builds the roster straight from whatever the admin
     * picked in ManualAssignmentPane, validates it against the two hard
     * constraints that can't be filtered per-slot, then reuses the exact
     * same PayrollCalculator (including the item #4 holiday premium) as the
     * automatic path.
     */
    private void computeManualPayroll() {
        if (manualPane == null || cachedEmployees == null || cachedShifts == null) {
            statusLabel.setText("Shift data is still loading - please wait a moment and try again.");
            return;
        }

        LocalDate weekStart = mondayOf(weekStartPicker.getValue() != null ? weekStartPicker.getValue() : LocalDate.now());
        Set<LocalDate> publicHolidays;
        try {
            publicHolidays = new PublicHolidayDao().findAllDates();
        } catch (Exception ex) {
            statusLabel.setText("Error loading public holidays: " + ex.getMessage());
            publicHolidays = Set.of();
        }

        List<RosterAssignment> assignments = manualPane.buildAssignments();
        ManualScheduleValidator.ValidationResult validation =
                new ManualScheduleValidator().validate(cachedEmployees, cachedShifts, assignments);

        manualPane.setWarningText(validation.isClean()
                ? ""
                : "Compliance warnings (NO_OVERLAP / MAX_HOURS):\n" + String.join("\n", validation.violations));

        Map<Integer, Shift> shiftsById = cachedShifts.stream()
                .collect(Collectors.toMap(Shift::getShiftId, s -> s));
        List<PayrollRecord> payroll = new PayrollCalculator()
                .calculate(cachedEmployees, shiftsById, assignments, weekStart, publicHolidays);

        ObservableList<PayrollRow> rows = FXCollections.observableArrayList();
        for (PayrollRecord p : payroll) {
            rows.add(new PayrollRow(
                    p.getEmployeeName(),
                    String.format("%.1f", p.getTotalHours()),
                    String.format("%.1f", p.getOvertimeHours()),
                    String.format("%.2f", p.getHolidayPay()),
                    String.format("%.2f", p.getTotalPay()),
                    String.format("%.2f", p.getStatutory().getEmployeeEpf()),
                    String.format("%.2f", p.getStatutory().getEmployeeSocso()),
                    String.format("%.2f", p.getStatutory().getEmployeeEis()),
                    String.format("%.2f", p.getStatutory().getNetPay())
            ));
        }
        payrollTable.setItems(rows);

        double totalNet = payroll.stream().mapToDouble(p -> p.getStatutory().getNetPay().doubleValue()).sum();
        double totalEmployerCost = payroll.stream().mapToDouble(p -> p.getStatutory().getEmployerTotalCost().doubleValue()).sum();
        long holidaysThisWeek = publicHolidays.stream()
                .filter(d -> !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7)))
                .count();
        PreferenceScorer.Result prefScore;
        try {
            prefScore = PreferenceScorer.score(assignments, new PreferenceDao().findAll());
        } catch (Exception ex) {
            prefScore = PreferenceScorer.score(assignments, List.of());
        }
        statusLabel.setText(String.format(
                "Manual mode | Week: %s | Coverage: %.0f%% (%d/%d slots filled) | Warnings: %d | Preferences granted: %d, avoided-but-assigned: %d (violation score %d) | Holidays this week: %d | Net payroll: RM%.2f | Employer cost: RM%.2f",
                weekStart, validation.coveragePercent(),
                validation.totalSlots - validation.unfilledSlots, validation.totalSlots,
                validation.violations.size(),
                prefScore.preferredGranted, prefScore.avoidedImposed, prefScore.violationScore,
                holidaysThisWeek, totalNet, totalEmployerCost));

        lastAssignments = assignments;
        lastPayroll = payroll;
        lastShiftsById = shiftsById;
        lastWeekStart = weekStart;
        lastScheduleStatus = "DRAFT";
        saveButton.setDisable(false);
    }

    /**
     * Item 4 (step 4 of the FYP1 plan): persists the most recently generated
     * roster + payroll into schedule / schedule_assignment / payroll_record.
     * Deliberately a separate button rather than auto-saving on every
     * Generate click, so re-running the solver or trying different manual
     * picks during testing doesn't clutter the schedule table with rows
     * that were never meant to be kept.
     */
    private void saveToDatabase() {
        if (lastAssignments == null || lastPayroll == null || lastShiftsById == null || lastWeekStart == null) {
            statusLabel.setText("Nothing to save yet - click 'Generate Roster & Payroll' first.");
            return;
        }
        final List<RosterAssignment> assignmentsToSave = lastAssignments;
        final List<PayrollRecord> payrollToSave = lastPayroll;
        final Map<Integer, Shift> shiftsByIdToSave = lastShiftsById;
        final LocalDate weekStartToSave = lastWeekStart;
        final String statusToSave = lastScheduleStatus;
        final Integer userId = currentUser != null ? currentUser.getUserId() : null;

        saveButton.setDisable(true);
        statusLabel.setText("Saving to database...");

        Thread worker = new Thread(() -> {
            try {
                int scheduleId = new ScheduleDao().create(weekStartToSave, statusToSave, null, userId);
                new ScheduleAssignmentDao().saveAll(scheduleId, assignmentsToSave, shiftsByIdToSave);
                new PayrollRecordDao().saveAll(scheduleId, payrollToSave);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText(String.format(
                            "Saved as schedule #%d (%s, week starting %s): %d assignment(s), %d payroll record(s).",
                            scheduleId, statusToSave, weekStartToSave, assignmentsToSave.size(), payrollToSave.size()));
                    saveButton.setDisable(false);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error saving to database: " + ex.getMessage());
                    saveButton.setDisable(false);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------- Employees tab ----------------

    private BorderPane buildEmployeesTab(Stage stage) {
        Button registerButton = new Button("Register New Employee");
        Button refreshButton = new Button("Refresh");
        registerButton.setOnAction(e -> openRegistrationDialog(stage));
        refreshButton.setOnAction(e -> loadEmployeesIntoTable());

        HBox topBar = new HBox(10, registerButton, refreshButton, employeeStatusLabel);
        topBar.setPadding(new Insets(10));

        setupEmployeeTableColumns(stage);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(employeeTable);
        return root;
    }

    private void setupEmployeeTableColumns(Stage stage) {
        TableColumn<EmployeeRow, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<EmployeeRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<EmployeeRow, String> positionCol = new TableColumn<>("Position");
        positionCol.setCellValueFactory(new PropertyValueFactory<>("position"));
        TableColumn<EmployeeRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        TableColumn<EmployeeRow, String> rateCol = new TableColumn<>("Rate (RM/hr)");
        rateCol.setCellValueFactory(new PropertyValueFactory<>("rate"));
        TableColumn<EmployeeRow, String> maxHoursCol = new TableColumn<>("Max Hrs/Wk");
        maxHoursCol.setCellValueFactory(new PropertyValueFactory<>("maxHours"));
        TableColumn<EmployeeRow, String> bankCol = new TableColumn<>("Bank Account");
        bankCol.setCellValueFactory(new PropertyValueFactory<>("bankAccount"));

        TableColumn<EmployeeRow, Void> availCol = new TableColumn<>("Availability");
        availCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Set Availability");
            {
                editButton.setOnAction(e -> {
                    EmployeeRow row = getTableView().getItems().get(getIndex());
                    AvailabilityEditDialog.show(stage, Integer.parseInt(row.getId()), row.getName(),
                            MainApp.this::loadEmployeesIntoTable);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<EmployeeRow, Void> prefCol = new TableColumn<>("Preferences");
        prefCol.setCellFactory(col -> new TableCell<>() {
            private final Button prefButton = new Button("Set Preferences");
            {
                prefButton.setOnAction(e -> {
                    EmployeeRow row = getTableView().getItems().get(getIndex());
                    PreferenceEditDialog.show(stage, Integer.parseInt(row.getId()), row.getName(), null);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : prefButton);
            }
        });

        employeeTable.getColumns().addAll(
                idCol, nameCol, positionCol, typeCol, rateCol, maxHoursCol, bankCol, availCol, prefCol);
    }

    private void openRegistrationDialog(Stage owner) {
        try {
            List<Role> roles = new RoleDao().findAll();
            if (roles.isEmpty()) {
                employeeStatusLabel.setText("No roles found in the 'role' table - add one first.");
                return;
            }
            EmployeeRegistrationDialog.show(owner, roles, this::loadEmployeesIntoTable);
        } catch (Exception ex) {
            employeeStatusLabel.setText("Error loading roles: " + ex.getMessage());
        }
    }

    private void loadEmployeesIntoTable() {
        Thread worker = new Thread(() -> {
            try {
                List<Role> roles = new RoleDao().findAll();
                roleNamesById = roles.stream()
                        .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));
                List<Employee> employees = new EmployeeDao().findAll();

                ObservableList<EmployeeRow> rows = FXCollections.observableArrayList();
                for (Employee e : employees) {
                    rows.add(new EmployeeRow(
                            String.valueOf(e.getEmployeeId()),
                            e.getFullName(),
                            roleNamesById.getOrDefault(e.getRoleId(), "Role #" + e.getRoleId()),
                            e.getEmploymentType().name(),
                            e.getHourlyRate().toPlainString(),
                            e.getMaxWeeklyHours().toPlainString(),
                            e.getBankAccountNumber() == null ? "" : e.getBankAccountNumber()
                    ));
                }
                javafx.application.Platform.runLater(() -> {
                    employeeTable.setItems(rows);
                    employeeStatusLabel.setText(employees.size() + " employee(s) loaded.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        employeeStatusLabel.setText("Error loading employees: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class PayrollRow {
        private final String name, hours, overtime, holidayPay, pay, epf, socso, eis, netPay;

        public PayrollRow(String name, String hours, String overtime, String holidayPay, String pay,
                           String epf, String socso, String eis, String netPay) {
            this.name = name;
            this.hours = hours;
            this.overtime = overtime;
            this.holidayPay = holidayPay;
            this.pay = pay;
            this.epf = epf;
            this.socso = socso;
            this.eis = eis;
            this.netPay = netPay;
        }

        public String getName() { return name; }
        public String getHours() { return hours; }
        public String getOvertime() { return overtime; }
        public String getHolidayPay() { return holidayPay; }
        public String getPay() { return pay; }
        public String getEpf() { return epf; }
        public String getSocso() { return socso; }
        public String getEis() { return eis; }
        public String getNetPay() { return netPay; }
    }

    public static class EmployeeRow {
        private final String id, name, position, type, rate, maxHours, bankAccount;

        public EmployeeRow(String id, String name, String position, String type,
                            String rate, String maxHours, String bankAccount) {
            this.id = id;
            this.name = name;
            this.position = position;
            this.type = type;
            this.rate = rate;
            this.maxHours = maxHours;
            this.bankAccount = bankAccount;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getPosition() { return position; }
        public String getType() { return type; }
        public String getRate() { return rate; }
        public String getMaxHours() { return maxHours; }
        public String getBankAccount() { return bankAccount; }
    }
}
