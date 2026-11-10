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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final String STYLESHEET = "/css/app.css";

    // --- Roster & Payroll tab ---
    private final TableView<PayrollRow> payrollTable = new TableView<>();
    private final Label statusLabel = new Label("Ready. Click 'Generate Roster & Payroll' to run.");
    private final RadioButton autoRadio = new RadioButton("Automatic (CP-SAT Solver)");
    private final RadioButton manualRadio = new RadioButton("Manual Assignment");
    private final DatePicker weekStartPicker = new DatePicker(mondayOf(LocalDate.now()));
    private final BorderPane rosterContent = new BorderPane();
    private final StatusSummaryPane summaryPane = new StatusSummaryPane();
    private boolean weekListenerAdded = false; // buildRosterTab runs on every admin login
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private List<Employee> cachedEmployees;
    private List<Shift> cachedShifts;
    private ManualAssignmentPane manualPane;
    private final Button saveButton = new Button("Save to Database");
    private User currentUser;
    private Company company;

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
        try {
            company = new com.questiu.scheduler.dao.CompanyDao().find();
        } catch (Exception ex) {
            company = null; // keep the login screen usable if the database is unreachable
        }
        stage.setTitle(company != null
                ? company.getCompanyName() + " - SmartShift: Shift Scheduling and Payroll"
                : "SmartShift: Shift Scheduling and Payroll");
        showLoginScreen(stage);
        stage.show();
    }

    private void showLoginScreen(Stage stage) {
        Scene scene = new Scene(LoginView.build(user -> showMainTabs(stage, user)), 440, 380);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
    }
        private void showStaffScreen(Stage stage, User staffUser) {
        currentUser = staffUser;
        Scene scene = new Scene(StaffHomeView.build(staffUser, company, () -> showLoginScreen(stage)), 1250, 680);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
    }

        private void showMainTabs(Stage stage, User loggedInUser) {
        if (loggedInUser.isStaff()) {
            showStaffScreen(stage, loggedInUser);
            return;
        }
        currentUser = loggedInUser;
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Roster & Payroll", buildRosterTab()));
        tabs.getTabs().add(new Tab("Employees", buildEmployeesTab(stage)));
        tabs.getTabs().add(new Tab("Public Holidays", new PublicHolidayPane()));
        tabs.getTabs().add(new Tab("Leave Requests", AdminLeavePane.build(loggedInUser)));
        tabs.getTabs().add(new Tab("Saved Schedules", new SavedSchedulesPane()));
        tabs.getTabs().forEach(t -> t.setClosable(false));

        Label sessionLabel = new Label(
                "Logged in as: " + loggedInUser.getFullName() + " (" + loggedInUser.getRole() + ")");
        Button logoutButton = new Button("Log Out");
        logoutButton.setOnAction(e -> showLoginScreen(stage));
        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.setOnAction(e -> ChangePasswordDialog.show(stage, loggedInUser));

        Label companyLabel = new Label(company != null ? company.getCompanyName() : "SmartShift");
        companyLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox sessionBar = new HBox(15, companyLabel, spacer, sessionLabel, changePasswordButton, logoutButton);
        sessionBar.setPadding(new Insets(10, 20, 10, 20));
        sessionBar.setAlignment(Pos.CENTER_LEFT);
        sessionBar.getStyleClass().add("topbar");

        BorderPane root = new BorderPane();
        root.setTop(sessionBar);
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1250, 680);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(950);
        stage.setMinHeight(500);
        stage.setWidth(1250);
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
        weekStartPicker.setConverter(DateFormats.dayMonthYear());
        weekStartPicker.setTooltip(new Tooltip(
                "The Monday of the week you're generating a roster for - matched against the Public Holidays calendar."));
        // Manual mode: when the week changes, hide people on approved leave for the new week
        // (the screen is not rebuilt, so the other picks are kept).
        if (!weekListenerAdded) {
            weekListenerAdded = true;
            weekStartPicker.valueProperty().addListener((obs, oldV, newV) -> {
                if (manualRadio.isSelected() && manualPane != null) {
                    refreshManualLeave();
                }
            });
        }

        Button generateButton = new Button("Generate Roster & Payroll");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setOnAction(e -> {
            if (manualRadio.isSelected()) {
                computeManualPayroll();
            } else {
                runAutoPipeline();
            }
        });

        saveButton.getStyleClass().add("success-button");
        saveButton.setDisable(true);
        saveButton.setTooltip(new Tooltip(
                "Persists the roster shown above into the schedule / schedule_assignment / payroll_record tables."));
        saveButton.setOnAction(e -> saveToDatabase());

        HBox controlsRow = new HBox(15, autoRadio, manualRadio,
                new Label("Week starting:"), weekStartPicker, generateButton, saveButton);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("status-label");
        HBox statusRow = new HBox(statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // The plain message line takes no space when it is empty (the summary block below shows results).
        statusRow.visibleProperty().bind(statusLabel.textProperty().isNotEmpty());
        statusRow.managedProperty().bind(statusLabel.textProperty().isNotEmpty());

        VBox topBar = new VBox(6, controlsRow, statusRow, summaryPane);
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

        // setAll, not addAll: the table is created once but this runs on every admin login
        payrollTable.getColumns().setAll(nameCol, hoursCol, otCol, holidayCol, payCol, epfCol, socsoCol, eisCol, netPayCol);
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
                    refreshManualLeave();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        statusLabel.setText("Error loading shift data: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    /** employeeId -> days of the roster week (1-7) on approved leave; same source the automatic mode uses. */
    private Map<Integer, Set<Integer>> loadLeaveDays(LocalDate weekStart) throws Exception {
        List<LeaveRequest> approvedLeave = new com.questiu.scheduler.dao.LeaveRequestDao()
                .findApprovedOverlapping(weekStart, weekStart.plusDays(6));
        return com.questiu.scheduler.solver.LeaveAvailabilityFilter.leaveDaysByEmployee(approvedLeave, weekStart);
    }

    /**
     * Manual mode: hides people on approved leave from the slot dropdowns for the
     * week in the picker. Keeps every pick that is still valid; a pick that is now on
     * leave is cleared and listed in the warning text under the table.
     */
    private void refreshManualLeave() {
        ManualAssignmentPane pane = manualPane;
        if (pane == null) return;
        LocalDate weekStart = mondayOf(weekStartPicker.getValue() != null ? weekStartPicker.getValue() : LocalDate.now());
        Thread worker = new Thread(() -> {
            try {
                Map<Integer, Set<Integer>> leaveDays = loadLeaveDays(weekStart);
                int leaveDayCount = com.questiu.scheduler.solver.LeaveAvailabilityFilter.countEmployeeDays(leaveDays);
                Set<LocalDate> holidays = new PublicHolidayDao().findAllDates();
                javafx.application.Platform.runLater(() -> {
                    pane.setWeek(weekStart, holidays);
                    summaryPane.clear(); // a result shown for another week would be misleading
                    List<String> cleared = pane.applyLeave(leaveDays, weekStart);
                    pane.setWarningText(cleared.isEmpty() ? ""
                            : "Picks cleared because of approved leave:\n" + String.join("\n", cleared));
                    if (manualRadio.isSelected()) {
                        statusLabel.setText("Pick who works each shift, then click 'Generate Roster & Payroll'. "
                                + "Week " + weekStart + ": approved leave hides " + leaveDayCount + " employee-day(s) from the lists.");
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        statusLabel.setText("Error loading approved leave: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private static String money(double amount) {
        return String.format("RM%,.2f", amount);
    }

    /** Result block for Automatic mode: one labelled row each for solver, week, preferences and payroll. */
    private void showAutoSummary(String solverStatus, long solveMillis, int unfilled,
                                 PreferenceScorer.Result prefScore, LocalDate weekStart,
                                 long holidaysThisWeek, int leaveDayCount,
                                 double totalNet, double totalEmployerCost) {
        Map<String, List<StatusSummaryPane.Item>> rows = new LinkedHashMap<>();
        rows.put("Solver", List.of(
                solverStatus.equals("OPTIMAL")
                        ? StatusSummaryPane.good("Status", solverStatus)
                        : StatusSummaryPane.warn("Status", solverStatus),
                StatusSummaryPane.item("Solve time", String.format("%,d ms", solveMillis)),
                StatusSummaryPane.warnIf(unfilled > 0, "Unfilled slots", String.valueOf(unfilled))));
        rows.put("Week", List.of(
                StatusSummaryPane.item("Week starting", weekStart.format(DMY)),
                StatusSummaryPane.item("Holidays this week", String.valueOf(holidaysThisWeek)),
                StatusSummaryPane.item("Leave days respected", String.valueOf(leaveDayCount))));
        rows.put("Preferences", preferenceItems(prefScore));
        rows.put("Payroll", List.of(
                StatusSummaryPane.item("Net payroll", money(totalNet)),
                StatusSummaryPane.item("Employer cost", money(totalEmployerCost))));
        summaryPane.showRows(rows);
    }

    /** Result block for Manual mode: coverage and warnings first, then the same week/preferences/payroll rows. */
    private void showManualSummary(ManualScheduleValidator.ValidationResult validation,
                                   PreferenceScorer.Result prefScore, LocalDate weekStart,
                                   long holidaysThisWeek, double totalNet, double totalEmployerCost) {
        Map<String, List<StatusSummaryPane.Item>> rows = new LinkedHashMap<>();
        rows.put("Roster", List.of(
                StatusSummaryPane.item("Mode", "Manual"),
                StatusSummaryPane.warnIf(validation.unfilledSlots > 0, "Coverage",
                        String.format("%.0f%% (%d of %d slots filled)", validation.coveragePercent(),
                                validation.totalSlots - validation.unfilledSlots, validation.totalSlots)),
                StatusSummaryPane.warnIf(!validation.violations.isEmpty(), "Warnings",
                        String.valueOf(validation.violations.size()))));
        rows.put("Week", List.of(
                StatusSummaryPane.item("Week starting", weekStart.format(DMY)),
                StatusSummaryPane.item("Holidays this week", String.valueOf(holidaysThisWeek))));
        rows.put("Preferences", preferenceItems(prefScore));
        rows.put("Payroll", List.of(
                StatusSummaryPane.item("Net payroll", money(totalNet)),
                StatusSummaryPane.item("Employer cost", money(totalEmployerCost))));
        summaryPane.showRows(rows);
    }

    private static List<StatusSummaryPane.Item> preferenceItems(PreferenceScorer.Result prefScore) {
        return List.of(
                StatusSummaryPane.item("Granted", String.valueOf(prefScore.preferredGranted)),
                StatusSummaryPane.warnIf(prefScore.avoidedImposed > 0, "Avoided but assigned",
                        String.valueOf(prefScore.avoidedImposed)),
                StatusSummaryPane.warnIf(prefScore.violationScore > 0, "Violation score",
                        String.valueOf(prefScore.violationScore)));
    }

    private void refreshRosterModeView() {
        summaryPane.clear();
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
                javafx.application.Platform.runLater(() -> {
                    summaryPane.clear();
                    statusLabel.setText("Loading data...");
                });

                List<Employee> employees = new EmployeeDao().findAllActive();
                System.out.println("[UI] Loaded " + employees.size() + " employees");
                List<Shift> shifts = new ShiftDao().findAll();
                System.out.println("[UI] Loaded " + shifts.size() + " shifts");
                List<Availability> availability = new AvailabilityDao().findAll();
                System.out.println("[UI] Loaded " + availability.size() + " availability rows");
                List<LeaveRequest> approvedLeave = new com.questiu.scheduler.dao.LeaveRequestDao()
                        .findApprovedOverlapping(weekStart, weekStart.plusDays(6));
                Map<Integer, Set<Integer>> leaveDays = com.questiu.scheduler.solver.LeaveAvailabilityFilter
                        .leaveDaysByEmployee(approvedLeave, weekStart);
                final int leaveDayCount = com.questiu.scheduler.solver.LeaveAvailabilityFilter
                        .countEmployeeDays(leaveDays);
                List<Availability> usableAvailability = com.questiu.scheduler.solver.LeaveAvailabilityFilter
                        .apply(availability, leaveDays);
                System.out.println("[UI] Approved leave removes " + leaveDayCount + " employee-day(s) this week");
                Set<LocalDate> publicHolidays = new PublicHolidayDao().findAllDates();
                System.out.println("[UI] Loaded " + publicHolidays.size() + " public holidays on file");

                javafx.application.Platform.runLater(() -> statusLabel.setText("Solving with CP-SAT..."));
                System.out.println("[UI] Calling SchedulingEngine.solve()...");
                List<Preference> preferences = new PreferenceDao().findAll();
                System.out.println("[UI] Loaded " + preferences.size() + " shift preferences");
                SchedulingEngine.SolveResult result = new SchedulingEngine()
                        .solve(employees, shifts, usableAvailability, preferences, 10.0);
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
                    statusLabel.setText("Roster generated for the week starting " + weekStart.format(DMY)
                            + ". Check it below, then click Save to Database.");
                    showAutoSummary(result.status.toString(), result.solveTimeMillis, result.totalUnfilled,
                            prefScore, weekStart, holidaysThisWeek, leaveDayCount, totalNet, totalEmployerCost);
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
        Map<Integer, Set<Integer>> leaveDays;
        try {
            leaveDays = loadLeaveDays(weekStart);
        } catch (Exception ex) {
            statusLabel.setText("Error loading approved leave: " + ex.getMessage());
            leaveDays = Map.of();
        }
        ManualScheduleValidator.ValidationResult validation =
                new ManualScheduleValidator().validate(cachedEmployees, cachedShifts, assignments, leaveDays, weekStart);

        manualPane.setWarningText(validation.isClean()
                ? ""
                : "Compliance warnings (NO_OVERLAP / MAX_HOURS / LEAVE):\n" + String.join("\n", validation.violations));

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
        statusLabel.setText("Manual roster calculated for the week starting " + weekStart.format(DMY)
                + ". Check it below, then click Save to Database.");
        showManualSummary(validation, prefScore, weekStart, holidaysThisWeek, totalNet, totalEmployerCost);

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
        registerButton.getStyleClass().add("primary-button");
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

       private static TableColumn<EmployeeRow, String> textColumn(String title, String property, double width) {
        TableColumn<EmployeeRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(width);
        return col;
    }

    private void setupEmployeeTableColumns(Stage stage) {
        TableColumn<EmployeeRow, String> idCol = textColumn("ID", "id", 45);
        TableColumn<EmployeeRow, String> nameCol = textColumn("Name", "name", 170);
        TableColumn<EmployeeRow, String> departmentCol = textColumn("Department", "department", 115);
        TableColumn<EmployeeRow, String> jobPositionCol = textColumn("Job Position", "jobPosition", 110);
        TableColumn<EmployeeRow, String> typeCol = textColumn("Type", "type", 85);
        TableColumn<EmployeeRow, String> rateCol = textColumn("Rate (RM/hr)", "rate", 90);
        TableColumn<EmployeeRow, String> maxHoursCol = textColumn("Max Hrs/Wk", "maxHours", 90);
        TableColumn<EmployeeRow, String> icCol = textColumn("IC No.", "icNumber", 110);
        TableColumn<EmployeeRow, String> epfCol = textColumn("EPF No.", "epfNumber", 115);
        TableColumn<EmployeeRow, String> socsoCol = textColumn("SOCSO No.", "socsoNumber", 125);
        TableColumn<EmployeeRow, String> eisCol = textColumn("EIS No.", "eisNumber", 115);
        TableColumn<EmployeeRow, String> joinedCol = textColumn("Joined", "dateJoined", 90);
        TableColumn<EmployeeRow, String> bankCol = textColumn("Bank Account", "bankAccount", 110);

        TableColumn<EmployeeRow, Void> availCol = new TableColumn<>("Availability");
        availCol.setPrefWidth(130);
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
        prefCol.setPrefWidth(130);
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

        // setAll, not addAll: the table is created once but this runs on every admin login
        TableColumn<EmployeeRow, Void> loginCol = new TableColumn<>("Login");
        loginCol.setPrefWidth(130);
        loginCol.setCellFactory(col -> new TableCell<>() {
            private final Button loginButton = new Button("Manage Login");
            {
                loginButton.setOnAction(e -> {
                    EmployeeRow row = getTableView().getItems().get(getIndex());
                    ManageLoginDialog.show(stage, Integer.parseInt(row.getId()), row.getName(), null);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : loginButton);
            }
        });

        employeeTable.getColumns().setAll(
                idCol, nameCol, departmentCol, jobPositionCol, typeCol, rateCol, maxHoursCol,
                icCol, epfCol, socsoCol, eisCol, joinedCol, bankCol, availCol, prefCol, loginCol);
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

        private static String orBlank(String value) {
        return value == null ? "" : value;
    }

    private void loadEmployeesIntoTable() {
        Thread worker = new Thread(() -> {
            try {
                List<Role> roles = new RoleDao().findAll();
                roleNamesById = roles.stream()
                        .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));
                Map<Integer, String> positionNamesById = new com.questiu.scheduler.dao.JobPositionDao().findNamesById();
                List<Employee> employees = new EmployeeDao().findAll();

                ObservableList<EmployeeRow> rows = FXCollections.observableArrayList();
                for (Employee e : employees) {
                    String jobPosition = e.getJobPositionId() == null
                            ? "(not set)"
                            : positionNamesById.getOrDefault(e.getJobPositionId(), "Position #" + e.getJobPositionId());
                    rows.add(new EmployeeRow(
                            String.valueOf(e.getEmployeeId()),
                            e.getFullName(),
                            roleNamesById.getOrDefault(e.getRoleId(), "Role #" + e.getRoleId()),
                            jobPosition,
                            e.getEmploymentType().name(),
                            e.getHourlyRate().toPlainString(),
                            e.getMaxWeeklyHours().toPlainString(),
                            orBlank(e.getBankAccountNumber()),
                            orBlank(e.getIcNumber()),
                            orBlank(e.getEpfNumber()),
                            orBlank(e.getSocsoNumber()),
                            orBlank(e.getEisNumber()),
                            e.getDateJoined() == null ? "" : e.getDateJoined().toString()
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
        private final String id, name, department, jobPosition, type, rate, maxHours, bankAccount,
                icNumber, epfNumber, socsoNumber, eisNumber, dateJoined;

        public EmployeeRow(String id, String name, String department, String jobPosition, String type,
                            String rate, String maxHours, String bankAccount,
                            String icNumber, String epfNumber, String socsoNumber,
                            String eisNumber, String dateJoined) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.jobPosition = jobPosition;
            this.type = type;
            this.rate = rate;
            this.maxHours = maxHours;
            this.bankAccount = bankAccount;
            this.icNumber = icNumber;
            this.epfNumber = epfNumber;
            this.socsoNumber = socsoNumber;
            this.eisNumber = eisNumber;
            this.dateJoined = dateJoined;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDepartment() { return department; }
        public String getJobPosition() { return jobPosition; }
        public String getType() { return type; }
        public String getRate() { return rate; }
        public String getMaxHours() { return maxHours; }
        public String getBankAccount() { return bankAccount; }
        public String getIcNumber() { return icNumber; }
        public String getEpfNumber() { return epfNumber; }
        public String getSocsoNumber() { return socsoNumber; }
        public String getEisNumber() { return eisNumber; }
        public String getDateJoined() { return dateJoined; }
    }
}