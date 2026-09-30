package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.PublicHolidayDao;
import com.questiu.scheduler.model.PublicHoliday;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Public Holidays management screen (Section 3.7 - supports item #4). Lets
 * the admin maintain the public_holiday table that the Roster & Payroll
 * pipeline checks against (see PayrollCalculator / Shift.actualDate) instead
 * of needing to edit the database directly.
 */
public class PublicHolidayPane extends BorderPane {

    public static class HolidayRow {
        private final int holidayId;
        private final String date;
        private final String description;

        HolidayRow(PublicHoliday h) {
            this.holidayId = h.getHolidayId();
            this.date = h.getDate().toString();
            this.description = h.getDescription();
        }

        public String getDate() { return date; }
        public String getDescription() { return description; }
    }

    private final TableView<HolidayRow> table = new TableView<>();
    private final Label statusLabel = new Label();

    public PublicHolidayPane() {
        TableColumn<HolidayRow, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(110);
        TableColumn<HolidayRow, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(320);
        TableColumn<HolidayRow, Void> deleteCol = new TableColumn<>("");
        deleteCol.setPrefWidth(90);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            {
                deleteButton.setOnAction(e -> {
                    HolidayRow row = getTableView().getItems().get(getIndex());
                    deleteHoliday(row.holidayId);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });
        table.getColumns().addAll(dateCol, descCol, deleteCol);

        DatePicker datePicker = new DatePicker();
        datePicker.setPrefWidth(150);
        TextField descField = new TextField();
        descField.setPromptText("Description (e.g. Merdeka Day)");
        descField.setPrefWidth(240);
        Button addButton = new Button("Add Holiday");
        addButton.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            String desc = descField.getText().trim();
            if (date == null || desc.isEmpty()) {
                statusLabel.setText("Pick a date and enter a description first.");
                return;
            }
            addHoliday(date, desc);
            datePicker.setValue(null);
            descField.clear();
        });

        HBox addBar = new HBox(10, new Label("Add:"), datePicker, descField, addButton);
        addBar.setAlignment(Pos.CENTER_LEFT);
        addBar.setPadding(new Insets(10));

        setTop(addBar);
        setCenter(table);
        setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(6, 10, 6, 10));

        refresh();
    }

    private void addHoliday(LocalDate date, String description) {
        Thread worker = new Thread(() -> {
            try {
                new PublicHolidayDao().add(date, description);
                javafx.application.Platform.runLater(this::refresh);
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error adding: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void deleteHoliday(int holidayId) {
        Thread worker = new Thread(() -> {
            try {
                new PublicHolidayDao().delete(holidayId);
                javafx.application.Platform.runLater(this::refresh);
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error deleting: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    /** Reloads the table from the database. */
    public void refresh() {
        Thread worker = new Thread(() -> {
            try {
                List<PublicHoliday> holidays = new PublicHolidayDao().findAll();
                ObservableList<HolidayRow> rows = FXCollections.observableArrayList();
                for (PublicHoliday h : holidays) rows.add(new HolidayRow(h));
                javafx.application.Platform.runLater(() -> {
                    table.setItems(rows);
                    statusLabel.setText(holidays.size() + " holiday(s) on file.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        statusLabel.setText("Error loading holidays: " + ex.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }
}
