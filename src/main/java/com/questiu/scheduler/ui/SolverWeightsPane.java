package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.SolverWeightsDao;
import com.questiu.scheduler.solver.SolverWeights;
import com.questiu.scheduler.solver.SolverWeightsParser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Administrator tab (FYP2, item 5c-3): the weights the roster solver uses to trade wages,
 * fairness and employee preferences against each other, in ringgit. They are stored in the
 * database (constraint_rule), read when "Generate Roster & Payroll" is clicked, and recorded
 * with every saved roster. The hard rules (availability, role, coverage, no overlap, weekly
 * hour cap) are not affected by anything on this screen.
 */
public final class SolverWeightsPane extends ScrollPane {

    private final SolverWeightsDao dao = new SolverWeightsDao();
    private final TextField unfilled = field("unfilledField");
    private final TextField workload = field("workloadField");
    private final TextField preference = field("preferenceField");
    private final TextField overtime = field("overtimeField");
    private final TextField cost = field("costField");
    private final Label message = new Label();

    public SolverWeightsPane() {
        Label heading = new Label("Solver Weights");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label hint = new Label("These numbers decide how the automatic roster trades wages, fairness and "
                + "employee preferences. All are in ringgit. The hard rules (availability, role, coverage, "
                + "no double-booking, weekly hour cap) are never changed by this screen.");
        hint.getStyleClass().add("status-label");
        hint.setWrapText(true);

        VBox rows = new VBox(16,
                row("Price of an unfilled position",
                        "How much leaving one position empty counts against a roster. Keep it very high so "
                                + "every shift is staffed. The program never lets it fall below 10 times the "
                                + "dearest single shift, whatever you type.",
                        unfilled, "RM per position", "10000"),
                row("Fairness (workload balance)",
                        "How much one hour of difference between the busiest and the least busy person in "
                                + "the same role counts. Higher = hours are shared more evenly, usually at a "
                                + "slightly higher wage bill.",
                        workload, "RM per hour, whole number", "5"),
                row("Preference",
                        "How much one level of an employee's shift preference is worth. A preference only "
                                + "wins when it is worth more than the wage difference between the people who "
                                + "could take the shift.",
                        preference, "RM per level", "2"),
                row("Overtime",
                        "Price of an overtime hour. Automatic rosters never contain overtime because the "
                                + "weekly cap is a hard rule, so this changes nothing there.",
                        overtime, "RM per hour, whole number", "100"),
                row("Cost multiplier",
                        "How much the wage bill counts. 1 = real pay. Higher = cheaper rosters matter more "
                                + "than fairness and preferences. 0 = ignore pay.",
                        cost, "times", "1"));

        Button saveButton = new Button("Save Weights");
        saveButton.getStyleClass().add("success-button");
        saveButton.setOnAction(e -> save());
        Button defaultsButton = new Button("Restore Defaults");
        defaultsButton.setOnAction(e -> restoreDefaults());
        Button reloadButton = new Button("Reload");
        reloadButton.setOnAction(e -> load());
        HBox buttons = new HBox(10, saveButton, defaultsButton, reloadButton);

        message.setWrapText(true);
        Label foot = new Label("Changes apply the next time you click Generate Roster & Payroll. "
                + "Rosters that are already saved keep the weights they were made with.");
        foot.getStyleClass().add("status-label");
        foot.setWrapText(true);

        VBox content = new VBox(14, heading, hint, rows, buttons, message, foot);
        content.setPadding(new Insets(20));
        content.setMaxWidth(900);
        setContent(content);
        setFitToWidth(true);
        setStyle("-fx-background-color: transparent;");

        load();
    }

    private static TextField field(String id) {
        TextField f = new TextField();
        f.setId(id);
        f.setPrefWidth(120);
        f.setMaxWidth(120);
        return f;
    }

    private static HBox row(String name, String explanation, TextField input, String unit, String defaultValue) {
        Label title = new Label(name);
        title.setStyle("-fx-font-weight: bold;");
        Label detail = new Label(explanation);
        detail.getStyleClass().add("status-label");
        detail.setWrapText(true);
        VBox text = new VBox(3, title, detail);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(520);

        Label unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("status-label");
        Label defaultLabel = new Label("Default: " + defaultValue);
        defaultLabel.getStyleClass().add("status-label");
        VBox side = new VBox(3, new HBox(8, input, unitLabel), defaultLabel);
        side.setMinWidth(300);

        HBox row = new HBox(20, text, side);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private void fill(SolverWeights w) {
        unfilled.setText(SolverWeightsParser.plain(w.getUnfilledRm()));
        workload.setText(String.valueOf(w.getWorkloadRmPerHour()));
        preference.setText(SolverWeightsParser.plain(w.getPreferenceRmPerLevel()));
        overtime.setText(String.valueOf(w.getOvertimeRmPerHour()));
        cost.setText(SolverWeightsParser.plain(w.getCostMultiplier()));
    }

    private void load() {
        SolverWeightsDao.Loaded loaded = dao.load();
        fill(loaded.weights);
        if (loaded.note == null) {
            message.setText("");
        } else {
            show("Showing built-in defaults. " + loaded.note, false);
        }
    }

    private void restoreDefaults() {
        fill(SolverWeights.defaults());
        show("Default values filled in. Click Save Weights to apply them.", true);
    }

    private void save() {
        SolverWeightsParser.Result parsed = SolverWeightsParser.parse(
                unfilled.getText(), workload.getText(), preference.getText(),
                overtime.getText(), cost.getText());
        if (parsed.error != null) {
            show(parsed.error + " Nothing was saved.", false);
            return;
        }
        try {
            dao.save(parsed.weights);
            fill(parsed.weights); // show the values as stored
            show("Saved. The new weights are used the next time you generate a roster.", true);
        } catch (Exception ex) {
            show("Could not save the weights: " + ex.getMessage(), false);
        }
    }

    private void show(String text, boolean good) {
        message.setStyle("-fx-text-fill: " + (good ? "#15803d" : "red") + ";");
        message.setText(text);
    }
}
