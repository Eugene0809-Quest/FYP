package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.LeaveRequestDao;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.LeaveRequest;
import com.questiu.scheduler.model.LeaveStatus;
import com.questiu.scheduler.model.LeaveType;
import com.questiu.scheduler.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The "Leave Requests" tab of the admin screen (FYP2, lecturer feedback:
 * the administrator approves or not, or does everything). The administrator
 * sees every request, approves or rejects pending ones (with an optional
 * note), and can record leave for a staff member directly, saved as already
 * approved - for example when the request arrives by phone.
 */
public class AdminLeavePane {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static Node build(User admin) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));

        Label heading = new Label("Leave Requests");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label message = new Label();
        message.setWrapText(true);

        CheckBox pendingOnly = new CheckBox("Show pending only");
        Button refreshButton = new Button("Refresh");
        HBox controls = new HBox(15, pendingOnly, refreshButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        TableView<LeaveRequest> table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable reload = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(
                        new LeaveRequestDao().findAll(pendingOnly.isSelected())));
            } catch (Exception ex) {
                show(message, "Could not load leave requests: " + ex.getMessage(), false);
            }
        };
        pendingOnly.setOnAction(e -> reload.run());
        refreshButton.setOnAction(e -> reload.run());

        table.getColumns().add(column("Employee", 150, r -> r.getEmployeeName()));
        table.getColumns().add(column("Type", 120, r -> r.getLeaveType().toString()));
        table.getColumns().add(column("From", 90, r -> DMY.format(r.getStartDate())));
        table.getColumns().add(column("To", 90, r -> DMY.format(r.getEndDate())));
        table.getColumns().add(column("Days", 45, r -> String.valueOf(r.getDays())));
        table.getColumns().add(column("Status", 80, r -> r.getStatus().toString()));
        table.getColumns().add(column("Reason", 150, r -> orBlank(r.getReason())));
        table.getColumns().add(column("Requested", 120, r -> DMY_TIME.format(r.getRequestedAt())));
        table.getColumns().add(column("Decided by", 110, r -> orBlank(r.getDeciderName())));
        table.getColumns().add(column("Note", 140, r -> orBlank(r.getAdminNote())));

        TableColumn<LeaveRequest, Void> decisionCol = new TableColumn<>("Decision");
        decisionCol.setPrefWidth(170);
        decisionCol.setCellFactory(col -> new TableCell<>() {
            private final Button approveButton = new Button("Approve");
            private final Button rejectButton = new Button("Reject");
            private final HBox buttons = new HBox(6, approveButton, rejectButton);
            {
                approveButton.setOnAction(e ->
                        approve(getTableView().getItems().get(getIndex()), admin, message, reload));
                rejectButton.setOnAction(e ->
                        reject(getTableView().getItems().get(getIndex()), admin, message, reload));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                LeaveRequest r = getTableView().getItems().get(getIndex());
                setGraphic(r.getStatus() == LeaveStatus.PENDING ? buttons : null);
            }
        });
        table.getColumns().add(decisionCol);

        box.getChildren().addAll(heading, controls, message, table, buildRecordForm(admin, message, reload));
        reload.run();
        return box;
    }

    private static void approve(LeaveRequest r, User admin, Label message, Runnable reload) {
        try {
            LeaveRequestDao dao = new LeaveRequestDao();
            if (dao.countOverlapping(r.getEmployeeId(), r.getStartDate(), r.getEndDate(), false) > 0) {
                show(message, "Cannot approve: " + r.getEmployeeName()
                        + " already has approved leave overlapping these dates.", false);
                return;
            }
            boolean done = dao.decide(r.getLeaveId(), LeaveStatus.APPROVED, admin.getUserId(), null);
            show(message, done ? "Approved " + r.getEmployeeName() + "'s request."
                    : "That request is no longer pending (it was already decided or cancelled).", done);
        } catch (Exception ex) {
            show(message, "Could not approve: " + ex.getMessage(), false);
        }
        reload.run();
    }

    private static void reject(LeaveRequest r, User admin, Label message, Runnable reload) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject leave request");
        dialog.setHeaderText("Reject " + r.getEmployeeName() + "'s request?");
        dialog.setContentText("Note to staff (optional):");
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) return; // the administrator closed the dialog: do nothing
        try {
            boolean done = new LeaveRequestDao().decide(r.getLeaveId(), LeaveStatus.REJECTED,
                    admin.getUserId(), blankToNull(answer.get()));
            show(message, done ? "Rejected " + r.getEmployeeName() + "'s request."
                    : "That request is no longer pending (it was already decided or cancelled).", done);
        } catch (Exception ex) {
            show(message, "Could not reject: " + ex.getMessage(), false);
        }
        reload.run();
    }

    /** The form that records leave for a staff member, saved as already approved. */
    private static Node buildRecordForm(User admin, Label message, Runnable reload) {
        Label title = new Label("Record leave for a staff member (saved as approved)");
        title.setStyle("-fx-font-weight: bold;");

        ComboBox<Employee> employeeBox = new ComboBox<>();
        employeeBox.setConverter(new StringConverter<Employee>() {
            @Override
            public String toString(Employee e) {
                return e == null ? "" : e.getFullName();
            }
            @Override
            public Employee fromString(String text) {
                return null;
            }
        });
        try {
            employeeBox.setItems(FXCollections.observableArrayList(new EmployeeDao().findAllActive()));
            if (!employeeBox.getItems().isEmpty()) employeeBox.getSelectionModel().selectFirst();
        } catch (Exception ex) {
            show(message, "Could not load employees: " + ex.getMessage(), false);
        }

        ComboBox<LeaveType> typeBox = new ComboBox<>(FXCollections.observableArrayList(LeaveType.values()));
        typeBox.getSelectionModel().select(LeaveType.ANNUAL);
        DatePicker fromPicker = new DatePicker(LocalDate.now());
        fromPicker.setConverter(DateFormats.dayMonthYear());
        DatePicker toPicker = new DatePicker(LocalDate.now());
        toPicker.setConverter(DateFormats.dayMonthYear());
        TextField reasonField = new TextField();
        reasonField.setPromptText("Reason (optional)");
        reasonField.setPrefWidth(260);
        Button recordButton = new Button("Record Approved Leave");
        recordButton.getStyleClass().add("success-button");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Employee:"), 0, 0);
        form.add(employeeBox, 1, 0);
        form.add(new Label("Leave type:"), 2, 0);
        form.add(typeBox, 3, 0);
        form.add(new Label("From:"), 4, 0);
        form.add(fromPicker, 5, 0);
        form.add(new Label("To:"), 6, 0);
        form.add(toPicker, 7, 0);
        form.add(new Label("Reason:"), 0, 1);
        form.add(reasonField, 1, 1, 3, 1);
        form.add(recordButton, 4, 1, 4, 1);

        recordButton.setOnAction(e -> {
            Employee employee = employeeBox.getValue();
            LeaveType type = typeBox.getValue();
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (employee == null || type == null || from == null || to == null) {
                show(message, "Choose an employee, a leave type and both dates.", false);
                return;
            }
            if (to.isBefore(from)) {
                show(message, "The end date cannot be before the start date.", false);
                return;
            }
            try {
                LeaveRequestDao dao = new LeaveRequestDao();
                if (dao.countOverlapping(employee.getEmployeeId(), from, to, true) > 0) {
                    show(message, employee.getFullName() + " already has a pending or approved request "
                            + "overlapping these dates. Approve or reject it in the list instead.", false);
                    return;
                }
                dao.create(employee.getEmployeeId(), type, from, to, blankToNull(reasonField.getText()),
                        LeaveStatus.APPROVED, admin.getUserId(), "Recorded by administrator");
                show(message, "Recorded approved leave for " + employee.getFullName() + ".", true);
                reasonField.clear();
                reload.run();
            } catch (Exception ex) {
                show(message, "Could not record the leave: " + ex.getMessage(), false);
            }
        });

        form.getChildren().stream().filter(n -> n instanceof Label)
                .forEach(n -> ((Label) n).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE));
        VBox box = new VBox(8, title, form);
        box.setPadding(new Insets(8, 0, 0, 0));
        return box;
    }

    private static TableColumn<LeaveRequest, String> column(String title, double width,
                                                            Function<LeaveRequest, String> value) {
        TableColumn<LeaveRequest, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private static void show(Label label, String text, boolean good) {
        label.setStyle("-fx-text-fill: " + (good ? "#15803d" : "red") + ";");
        label.setText(text);
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
