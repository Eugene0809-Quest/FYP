package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.ScheduleViewDao;
import com.questiu.scheduler.dao.ScheduleViewDao.ScheduleSummary;
import com.questiu.scheduler.dao.ScheduleViewDao.ShiftRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Administrator tab (FYP2): every roster saved from the Roster & Payroll tab,
 * with a timetable view of who works which shift, and Approve & Publish /
 * Unpublish. Staff only ever see schedules that are published here.
 */
public final class SavedSchedulesPane extends VBox {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String STYLESHEET = "/css/app.css";

    private final ScheduleViewDao dao = new ScheduleViewDao();
    private final TableView<ScheduleSummary> table = new TableView<>();
    private final Label message = new Label();
    private final Button viewButton = new Button("View Timetable");
    private final Button publishButton = new Button("Approve & Publish to Staff");
    private final Button unpublishButton = new Button("Unpublish");

    public SavedSchedulesPane() {
        setSpacing(12);
        setPadding(new Insets(20));

        Label heading = new Label("Saved Schedules");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label hint = new Label("Save a roster from the Roster & Payroll tab, then publish it here. "
                + "Staff see only published schedules.");
        hint.getStyleClass().add("status-label");
        hint.setWrapText(true);

        TableColumn<ScheduleSummary, String> idCol = column("ID", 60, s -> String.valueOf(s.getScheduleId()));
        TableColumn<ScheduleSummary, String> weekCol = column("Week starting", 130, s -> DMY.format(s.getWeekStart()));
        TableColumn<ScheduleSummary, String> statusCol = column("Status", 110, s -> statusLabel(s.getStatus()));
        TableColumn<ScheduleSummary, String> countCol = column("Shifts assigned", 120,
                s -> String.valueOf(s.getAssignmentCount()));
        TableColumn<ScheduleSummary, String> byCol = column("Saved by", 160,
                s -> s.getSavedBy() == null ? "" : s.getSavedBy());
        TableColumn<ScheduleSummary, String> atCol = column("Saved at", 160,
                s -> s.getSavedAt() == null ? "" : DMY_TIME.format(s.getSavedAt()));
        table.getColumns().add(idCol);
        table.getColumns().add(weekCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(countCol);
        table.getColumns().add(byCol);
        table.getColumns().add(atCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> reload());
        viewButton.setOnAction(e -> openTimetable(table.getSelectionModel().getSelectedItem()));
        publishButton.getStyleClass().add("success-button");
        publishButton.setOnAction(e -> publish(table.getSelectionModel().getSelectedItem()));
        unpublishButton.setOnAction(e -> unpublish(table.getSelectionModel().getSelectedItem()));

        HBox buttons = new HBox(10, viewButton, publishButton, unpublishButton, refreshButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldV, sel) -> updateButtons(sel));
        updateButtons(null);

        message.setWrapText(true);
        getChildren().addAll(heading, hint, buttons, message, table);
        reload();
    }

    private static String statusLabel(String status) {
        switch (status) {
            case "APPROVED": return "Published";
            case "GENERATED": return "Generated";
            default: return "Draft";
        }
    }

    private void updateButtons(ScheduleSummary sel) {
        boolean none = sel == null;
        boolean empty = !none && sel.getAssignmentCount() == 0;
        boolean published = !none && "APPROVED".equals(sel.getStatus());
        viewButton.setDisable(none || empty);
        publishButton.setDisable(none || empty || published);
        unpublishButton.setDisable(none || !published);
    }

    private void reload() {
        try {
            table.setItems(FXCollections.observableArrayList(dao.findSchedules()));
            message.setText("");
        } catch (Exception ex) {
            showMessage("Could not load schedules: " + ex.getMessage(), true);
        }
        updateButtons(null);
    }

    private void publish(ScheduleSummary sel) {
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Publish schedule #" + sel.getScheduleId() + " (week starting " + DMY.format(sel.getWeekStart())
                        + ") to staff?\nAny schedule already published for that week will be replaced.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Publish schedule");
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) return;
        try {
            int replaced = dao.approve(sel.getScheduleId());
            if (replaced < 0) {
                showMessage("Schedule #" + sel.getScheduleId()
                        + " could not be published (it may already be published or have no shifts).", true);
            } else {
                showMessage("Published schedule #" + sel.getScheduleId() + " to staff."
                        + (replaced > 0 ? " " + replaced + " earlier schedule(s) for that week were replaced." : ""),
                        false);
            }
            reload();
        } catch (Exception ex) {
            showMessage("Could not publish: " + ex.getMessage(), true);
        }
    }

    private void unpublish(ScheduleSummary sel) {
        if (sel == null) return;
        try {
            boolean ok = dao.unpublish(sel.getScheduleId());
            showMessage(ok ? "Schedule #" + sel.getScheduleId() + " is no longer visible to staff."
                           : "That schedule was not published.", !ok);
            reload();
        } catch (Exception ex) {
            showMessage("Could not unpublish: " + ex.getMessage(), true);
        }
    }

    private void openTimetable(ScheduleSummary sel) {
        if (sel == null) return;
        List<ShiftRow> rows;
        try {
            rows = dao.findAssignments(sel.getScheduleId());
        } catch (Exception ex) {
            showMessage("Could not load the timetable: " + ex.getMessage(), true);
            return;
        }
        TableView<ShiftRow> shiftTable = new TableView<>(FXCollections.observableArrayList(rows));
        TableColumn<ShiftRow, String> dateCol = shiftColumn("Date", 110, r -> DMY.format(r.getDate()));
        TableColumn<ShiftRow, String> dayCol = shiftColumn("Day", 110,
                r -> r.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        TableColumn<ShiftRow, String> startCol = shiftColumn("Start", 70, r -> HM.format(r.getStartTime()));
        TableColumn<ShiftRow, String> endCol = shiftColumn("End", 70, r -> HM.format(r.getEndTime()));
        TableColumn<ShiftRow, String> deptCol = shiftColumn("Department", 140, ShiftRow::getDepartment);
        TableColumn<ShiftRow, String> whoCol = shiftColumn("Employee", 200, ShiftRow::getEmployeeName);
        TableColumn<ShiftRow, String> hoursCol = shiftColumn("Hours", 70, r -> String.format("%.1f", r.getHours()));
        shiftTable.getColumns().add(dateCol);
        shiftTable.getColumns().add(dayCol);
        shiftTable.getColumns().add(startCol);
        shiftTable.getColumns().add(endCol);
        shiftTable.getColumns().add(deptCol);
        shiftTable.getColumns().add(whoCol);
        shiftTable.getColumns().add(hoursCol);
        VBox.setVgrow(shiftTable, Priority.ALWAYS);

        Label heading = new Label("Schedule #" + sel.getScheduleId() + " - week starting "
                + DMY.format(sel.getWeekStart()) + " (" + statusLabel(sel.getStatus()) + ", "
                + rows.size() + " shift assignments)");
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox root = new VBox(10, heading, shiftTable);
        root.setPadding(new Insets(15));

        Stage stage = new Stage();
        Window owner = getScene() == null ? null : getScene().getWindow();
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Timetable - schedule #" + sel.getScheduleId());
        Scene scene = new Scene(root, 880, 560);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setStyle(error ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
    }

    private static TableColumn<ScheduleSummary, String> column(String title, double width,
                                                               Function<ScheduleSummary, String> text) {
        TableColumn<ScheduleSummary, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(text.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private static TableColumn<ShiftRow, String> shiftColumn(String title, double width,
                                                             Function<ShiftRow, String> text) {
        TableColumn<ShiftRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(text.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }
}
