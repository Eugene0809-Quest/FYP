package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.*;
import com.questiu.scheduler.payroll.PayrollCalculator;
import com.questiu.scheduler.solver.SchedulingEngine;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainApp extends Application {

    private final TableView<PayrollRow> table = new TableView<>();
    private final Label statusLabel = new Label("Ready. Click 'Generate Roster & Payroll' to run.");

    @Override
    public void start(Stage stage) {
        Button generateButton = new Button("Generate Roster & Payroll");
        generateButton.setOnAction(e -> runPipeline());

        HBox topBar = new HBox(10, generateButton, statusLabel);
        topBar.setPadding(new Insets(10));

        setupTableColumns();

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(table);

        Scene scene = new Scene(root, 800, 500);
        stage.setTitle("Constraint-Based Shift Scheduling and Payroll Optimisation (FYP1 Prototype)");
        stage.setScene(scene);
        stage.show();
    }

    private void setupTableColumns() {
        TableColumn<PayrollRow, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<PayrollRow, String> hoursCol = new TableColumn<>("Total Hours");
        hoursCol.setCellValueFactory(new PropertyValueFactory<>("hours"));

        TableColumn<PayrollRow, String> otCol = new TableColumn<>("Overtime Hours");
        otCol.setCellValueFactory(new PropertyValueFactory<>("overtime"));

        TableColumn<PayrollRow, String> payCol = new TableColumn<>("Total Pay (RM)");
        payCol.setCellValueFactory(new PropertyValueFactory<>("pay"));

        table.getColumns().addAll(nameCol, hoursCol, otCol, payCol);
    }

    private void runPipeline() {
        Thread worker = new Thread(() -> {
            try {
                System.out.println("[UI] Starting pipeline...");
                javafx.application.Platform.runLater(() -> statusLabel.setText("Loading data..."));

                int weekPlanId = 1;
                List<Employee> employees = new EmployeeDao().findAllActive();
                System.out.println("[UI] Loaded " + employees.size() + " employees");
                List<Shift> shifts = new ShiftDao().findByWeekPlan(weekPlanId);
                System.out.println("[UI] Loaded " + shifts.size() + " shifts");
                List<Availability> availability = new AvailabilityDao().findByWeekPlan(weekPlanId);
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
                    table.setItems(rows);
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
}