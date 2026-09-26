package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.RoleDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.*;
import com.questiu.scheduler.payroll.PayrollCalculator;
import com.questiu.scheduler.solver.ManualScheduleValidator;
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
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Multi-tab prototype UI (Section 3.7). Split into "Roster & Payroll" (which
 * now offers item #3, an Automatic/Manual toggle - see buildRosterTab) and
 * "Employees" (registration + listing) - added per supervisor feedback
 * requesting a more complete, multi-page feel rather than a single
 * generate-button screen.
 */
public class MainApp extends Application {

    // --- Roster & Payroll tab ---
    private final TableView<PayrollRow> payrollTable = new TableView<>();
    private final Label statusLabel = new Label("Ready. Click 'Generate Roster & Payroll' to run.");
    private final RadioButton autoRadio = new RadioButton("Automatic (CP-SAT Solver)");
    private final RadioButton manualRadio = new RadioButton("Manual Assignment");
    private final BorderPane rosterContent = new BorderPane();
    private List<Employee> cachedEmployees;
    private List<Shift> cachedShifts;
    private ManualAssignmentPane manualPane;

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
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Roster & Payroll", buildRosterTab()));
        tabs.getTabs().add(new Tab("Employees", buildEmployeesTab(stage)));
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

        stage.setScene(new Scene(root, 850, 580));
        loadEmployeesIntoTable();
    }

    // ---------------- Roster & Payroll tab ----------------

    /**
     * Item #3 (supervisor-requested): an Automatic/Manual radio toggle.
     * Automatic keeps running the existing CP-SAT pipeline unchanged.
     * Manual shows ManualAssignmentPane instead and skips the solver
     * entirely - see computeManualPayroll(). Both paths end at the same
     * PayrollCalculator call, so a manual roster and a solved roster are
     * always priced identically (Section 3.5 / 2.2.3).
     */
    private BorderPane buildRosterTab() {
        ToggleGroup modeGroup = new ToggleGroup();
        autoRadio.setToggleGroup(modeGroup);
        manualRadio.setToggleGroup(modeGroup);
        autoRadio.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> refreshRosterModeView());

        Button generateButton = new Button("Generate Roster & Payroll");
        generateButton.setOnAction(e -> {
            if (manualRadio.isSelected()) {
                computeManualPayroll();
            } else {
                runAutoPipeline();
            }
        });

        HBox topBar = new HBox(15, autoRadio, manualRadio, generateButton, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
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

        TableColumn<PayrollRow, String> payCol = new TableColumn<>("Total Pay (RM)");
        payCol.setCellValueFactory(new PropertyValueFactory<>("pay"));

        payrollTable.getColumns().addAll(nameCol, hoursCol, otCol, payCol);
    }

    /** Loads employees/shifts/availability once so Manual mode has candidates without re-querying MySQL on every toggle. */
    private void loadShiftDataForRoster() {
        Thread worker = new Thread(() -> {
            try {
                List<Employee> employees = new EmployeeDao().findAllActive();
                List<Shift> shifts = new ShiftDao().findAll();
                List<Availability> availability = new AvailabilityDao().findAll();
                javafx.application.Platform.runLater(() -> {
                    cachedEmployees = employees;
                    cachedShifts = shifts;
                    manualPane = new ManualAssignmentPane(employees, shifts, availability);
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

    private void runAutoPipeline() {
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

                javafx.application.Platform.runLater(() -> statusLabel.setText("Solving with CP-SAT..."));
                System.out.println("[UI] Calling SchedulingEngine.solve()...");
                SchedulingEngine.SolveResult result = new SchedulingEngine()
                        .solve(employees, shifts, availability, 10.0);
                System.out.println("[UI] Solve returned. Status: " + result.status);

                if (!result.isFeasible()) {
                    javafx.application.Platform.runLater(() ->
                            statusLabel.setText("No feasible schedule found (status: " + result.status + ")"));
                    return;
                }

                Map<Integer, Shift> shiftsById = shifts.stream()
                        .collect(Collectors.toMap(Shift::getShiftId, s -> s));
                List<PayrollRecord> payroll = new PayrollCalculator()
                        .calculate(employees, shiftsById, result.assignments);
                System.out.println("[UI] Payroll calculated for " + payroll.size() + " employees");

                ObservableList<PayrollRow> rows = FXCollections.observableArrayList();
                for (PayrollRecord p : payroll) {
                    rows.add(new PayrollRow(
                            p.getEmployeeName(),
                            String.format("%.1f", p.getTotalHours()),
                            String.format("%.1f", p.getOvertimeHours()),
                            String.format("%.2f", p.getTotalPay())
                    ));
                }

                javafx.application.Platform.runLater(() -> {
                    payrollTable.setItems(rows);
                    statusLabel.setText(String.format("Status: %s | Solve time: %d ms | Unfilled slots: %d",
                            result.status, result.solveTimeMillis, result.totalUnfilled));
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
     * same PayrollCalculator as the automatic path.
     */
    private void computeManualPayroll() {
        if (manualPane == null || cachedEmployees == null || cachedShifts == null) {
            statusLabel.setText("Shift data is still loading - please wait a moment and try again.");
            return;
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
                .calculate(cachedEmployees, shiftsById, assignments);

        ObservableList<PayrollRow> rows = FXCollections.observableArrayList();
        for (PayrollRecord p : payroll) {
            rows.add(new PayrollRow(
                    p.getEmployeeName(),
                    String.format("%.1f", p.getTotalHours()),
                    String.format("%.1f", p.getOvertimeHours()),
                    String.format("%.2f", p.getTotalPay())
            ));
        }
        payrollTable.setItems(rows);

        statusLabel.setText(String.format(
                "Manual mode | Coverage: %.0f%% (%d/%d slots filled) | Warnings: %d",
                validation.coveragePercent(),
                validation.totalSlots - validation.unfilledSlots, validation.totalSlots,
                validation.violations.size()));
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

        employeeTable.getColumns().addAll(
                idCol, nameCol, positionCol, typeCol, rateCol, maxHoursCol, bankCol, availCol);
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
        private final String name, hours, overtime, pay;

        public PayrollRow(String name, String hours, String overtime, String pay) {
            this.name = name;
            this.hours = hours;
            this.overtime = overtime;
            this.pay = pay;
        }

        public String getName() { return name; }
        public String getHours() { return hours; }
        public String getOvertime() { return overtime; }
        public String getPay() { return pay; }
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
