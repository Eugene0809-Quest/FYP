package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.LeaveRequestDao;
import com.questiu.scheduler.model.LeaveRequest;
import com.questiu.scheduler.model.LeaveStatus;
import com.questiu.scheduler.model.LeaveType;
import com.questiu.scheduler.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * The "My Leave" tab of the staff screen (FYP2, lecturer feedback: staff ask
 * for leave and the administrator approves or not). A staff member can send
 * a request, see its status and the administrator's note, and cancel a
 * request that is still pending. They only ever see their own requests.
 */
public class StaffLeavePane {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static Node build(User user) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(20));

        Label heading = new Label("My Leave");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Integer employeeId = user.getEmployeeId();
        if (employeeId == null) {
            box.getChildren().addAll(heading,
                    new Label("This login is not linked to an employee record. Please ask an administrator."));
            return box;
        }

        ComboBox<LeaveType> typeBox = new ComboBox<>(FXCollections.observableArrayList(LeaveType.values()));
        typeBox.getSelectionModel().select(LeaveType.ANNUAL);
        DatePicker fromPicker = new DatePicker(LocalDate.now());
        fromPicker.setConverter(DateFormats.dayMonthYear());
        DatePicker toPicker = new DatePicker(LocalDate.now());
        toPicker.setConverter(DateFormats.dayMonthYear());
        TextField reasonField = new TextField();
        reasonField.setPromptText("Reason (optional)");
        reasonField.setPrefWidth(320);
        Button submitButton = new Button("Submit Request");
        submitButton.getStyleClass().add("primary-button");
        Label message = new Label();
        message.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Leave type:"), 0, 0);
        form.add(typeBox, 1, 0);
        form.add(new Label("From:"), 2, 0);
        form.add(fromPicker, 3, 0);
        form.add(new Label("To:"), 4, 0);
        form.add(toPicker, 5, 0);
        form.add(new Label("Reason:"), 0, 1);
        form.add(reasonField, 1, 1, 3, 1);
        form.add(submitButton, 4, 1, 2, 1);

        TableView<LeaveRequest> table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable reload = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(
                        new LeaveRequestDao().findByEmployee(employeeId)));
            } catch (Exception ex) {
                show(message, "Could not load your requests: " + ex.getMessage(), false);
            }
        };

        table.getColumns().add(column("Type", 130, r -> r.getLeaveType().toString()));
        table.getColumns().add(column("From", 95, r -> DMY.format(r.getStartDate())));
        table.getColumns().add(column("To", 95, r -> DMY.format(r.getEndDate())));
        table.getColumns().add(column("Days", 50, r -> String.valueOf(r.getDays())));
        table.getColumns().add(column("Status", 90, r -> r.getStatus().toString()));
        table.getColumns().add(column("Reason", 170, r -> orBlank(r.getReason())));
        table.getColumns().add(column("Decided by", 130, r -> orBlank(r.getDeciderName())));
        table.getColumns().add(column("Admin note", 170, r -> orBlank(r.getAdminNote())));
        table.getColumns().add(column("Requested", 125, r -> DMY_TIME.format(r.getRequestedAt())));

        TableColumn<LeaveRequest, Void> cancelCol = new TableColumn<>("");
        cancelCol.setPrefWidth(140);
        cancelCol.setCellFactory(col -> new TableCell<>() {
            private final Button cancelButton = new Button("Cancel request");
            {
                cancelButton.setOnAction(e -> {
                    LeaveRequest r = getTableView().getItems().get(getIndex());
                    try {
                        boolean done = new LeaveRequestDao().cancel(r.getLeaveId(), employeeId);
                        show(message, done ? "Request cancelled."
                                : "That request can no longer be cancelled (it was already decided).", done);
                    } catch (Exception ex) {
                        show(message, "Could not cancel: " + ex.getMessage(), false);
                    }
                    reload.run();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                LeaveRequest r = getTableView().getItems().get(getIndex());
                setGraphic(r.getStatus() == LeaveStatus.PENDING ? cancelButton : null);
            }
        });
        table.getColumns().add(cancelCol);

        submitButton.setOnAction(e -> {
            LeaveType type = typeBox.getValue();
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (type == null || from == null || to == null) {
                show(message, "Choose a leave type and both dates.", false);
                return;
            }
            if (to.isBefore(from)) {
                show(message, "The end date cannot be before the start date.", false);
                return;
            }
            boolean mayBackdate = type == LeaveType.SICK || type == LeaveType.EMERGENCY;
            if (from.isBefore(LocalDate.now()) && !mayBackdate) {
                show(message, "The start date cannot be in the past (only sick or emergency leave can be backdated).", false);
                return;
            }
            try {
                LeaveRequestDao dao = new LeaveRequestDao();
                if (dao.countOverlapping(employeeId, from, to, true) > 0) {
                    show(message, "You already have a pending or approved request that overlaps these dates.", false);
                    return;
                }
                dao.create(employeeId, type, from, to, blankToNull(reasonField.getText()),
                        LeaveStatus.PENDING, null, null);
                show(message, "Request submitted. It is waiting for the administrator's decision.", true);
                reasonField.clear();
                reload.run();
            } catch (Exception ex) {
                show(message, "Could not submit the request: " + ex.getMessage(), false);
            }
        });

        form.getChildren().stream().filter(n -> n instanceof Label)
                .forEach(n -> ((Label) n).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE));
        box.getChildren().addAll(heading, form, message, table);
        reload.run();
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
