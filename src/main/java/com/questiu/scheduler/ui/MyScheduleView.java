package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.ScheduleViewDao;
import com.questiu.scheduler.dao.ScheduleViewDao.ShiftRow;
import com.questiu.scheduler.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The staff "My Schedule" tab (FYP2): the logged-in employee's own shifts from
 * schedules the administrator has PUBLISHED. Drafts are never shown.
 */
public class MyScheduleView {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static Node build(User user) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(20));

        Label heading = new Label("My Schedule");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label message = new Label();
        message.setWrapText(true);
        box.getChildren().add(heading);

        if (user.getEmployeeId() == null) {
            message.setText("This login is not linked to an employee record. Please ask an administrator.");
            box.getChildren().add(message);
            return box;
        }

        List<ShiftRow> loaded;
        try {
            loaded = new ScheduleViewDao().findPublishedShifts(user.getEmployeeId());
        } catch (Exception ex) {
            message.setText("Could not load your schedule: " + ex.getMessage());
            box.getChildren().add(message);
            return box;
        }
        if (loaded.isEmpty()) {
            message.setText("No schedule has been published for you yet. "
                    + "When your administrator publishes a roster, your shifts appear here.");
            box.getChildren().add(message);
            return box;
        }
        final List<ShiftRow> shifts = loaded;

        List<LocalDate> weeks = shifts.stream().map(ShiftRow::getWeekStart)
                .distinct().sorted().collect(Collectors.toList());
        LocalDate today = LocalDate.now();
        LocalDate initial = weeks.stream().filter(w -> !w.plusDays(6).isBefore(today))
                .findFirst().orElse(weeks.get(weeks.size() - 1));

        ComboBox<LocalDate> weekBox = new ComboBox<>(FXCollections.observableArrayList(weeks));
        weekBox.setConverter(DateFormats.dayMonthYear());
        weekBox.setValue(initial);
        HBox topRow = new HBox(10, new Label("Week starting:"), weekBox);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label summary = new Label();
        summary.getStyleClass().add("status-label");

        TableView<ShiftRow> table = new TableView<>();
        TableColumn<ShiftRow, String> dateCol = column("Date", 110, r -> DMY.format(r.getDate()));
        TableColumn<ShiftRow, String> dayCol = column("Day", 110,
                r -> r.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        TableColumn<ShiftRow, String> startCol = column("Start", 80, r -> HM.format(r.getStartTime()));
        TableColumn<ShiftRow, String> endCol = column("End", 80, r -> HM.format(r.getEndTime()));
        TableColumn<ShiftRow, String> hoursCol = column("Hours", 80, r -> String.format("%.1f", r.getHours()));
        TableColumn<ShiftRow, String> deptCol = column("Department", 150, ShiftRow::getDepartment);
        table.getColumns().add(dateCol);
        table.getColumns().add(dayCol);
        table.getColumns().add(startCol);
        table.getColumns().add(endCol);
        table.getColumns().add(hoursCol);
        table.getColumns().add(deptCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> {
            LocalDate week = weekBox.getValue();
            List<ShiftRow> mine = shifts.stream().filter(s -> s.getWeekStart().equals(week))
                    .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(mine));
            double hours = mine.stream().mapToDouble(ShiftRow::getHours).sum();
            summary.setText(String.format("%d shift(s), %.1f hours in the week starting %s",
                    mine.size(), hours, DMY.format(week)));
        };
        weekBox.valueProperty().addListener((obs, oldV, newV) -> refresh.run());
        refresh.run();

        box.getChildren().addAll(topRow, summary, table);
        return box;
    }

    private static TableColumn<ShiftRow, String> column(String title, double width,
                                                        Function<ShiftRow, String> text) {
        TableColumn<ShiftRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(text.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }
}
