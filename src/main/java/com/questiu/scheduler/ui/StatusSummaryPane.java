package com.questiu.scheduler.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * A small panel that shows the result of a Generate click as labelled values
 * instead of one long sentence. Each row has a short title (Solver, Week,
 * Preferences, Payroll) and a few "label value" pairs: the label is small and
 * grey, the value is bold. A value that needs attention (unfilled slots,
 * warnings, a non-optimal solver status) is shown in amber, and a good one
 * (OPTIMAL) in green, so a problem is easy to spot at a glance.
 *
 * The panel hides itself (and takes no space) when it has nothing to show.
 */
public class StatusSummaryPane extends VBox {

    public enum Level { NORMAL, GOOD, WARN }

    /** One "label value" pair, e.g. ("Unfilled slots", "0", NORMAL). */
    public static final class Item {
        final String label;
        final String value;
        final Level level;

        Item(String label, String value, Level level) {
            this.label = label;
            this.value = value;
            this.level = level;
        }
    }

    public static Item item(String label, String value) {
        return new Item(label, value, Level.NORMAL);
    }

    public static Item good(String label, String value) {
        return new Item(label, value, Level.GOOD);
    }

    public static Item warn(String label, String value) {
        return new Item(label, value, Level.WARN);
    }

    /** Amber when {@code problem} is true, normal otherwise. */
    public static Item warnIf(boolean problem, String label, String value) {
        return problem ? warn(label, value) : item(label, value);
    }

    private static final String TITLE_STYLE =
            "-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;";
    private static final String LABEL_STYLE = "-fx-text-fill: #64748b; -fx-font-size: 12px;";

    public StatusSummaryPane() {
        setSpacing(8);
        setPadding(new Insets(10, 16, 10, 16));
        setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; "
                + "-fx-border-radius: 8; -fx-background-radius: 8;");
        clear();
    }

    /** Shows the given rows (title -> items), replacing whatever was shown before. */
    public void showRows(Map<String, List<Item>> rows) {
        getChildren().clear();
        boolean first = true;
        for (Map.Entry<String, List<Item>> row : rows.entrySet()) {
            if (!first) {
                // thin divider so each row reads as its own line
                Region divider = new Region();
                divider.setMinHeight(1);
                divider.setPrefHeight(1);
                divider.setMaxHeight(1);
                divider.setStyle("-fx-background-color: #eef2f7;");
                getChildren().add(divider);
            }
            first = false;

            Label title = new Label(row.getKey().toUpperCase());
            title.setStyle(TITLE_STYLE);
            title.setMinWidth(104);
            title.setPrefWidth(104);

            FlowPane items = new FlowPane(34, 4);
            for (Item item : row.getValue()) {
                items.getChildren().add(buildItem(item));
            }
            HBox.setHgrow(items, javafx.scene.layout.Priority.ALWAYS);

            HBox line = new HBox(14, title, items);
            line.setAlignment(Pos.TOP_LEFT);
            line.setPadding(new Insets(2, 0, 2, 0));
            getChildren().add(line);
        }
        setVisible(!rows.isEmpty());
        setManaged(!rows.isEmpty());
    }

    public void clear() {
        getChildren().clear();
        setVisible(false);
        setManaged(false);
    }

    private static HBox buildItem(Item item) {
        Label label = new Label(item.label);
        label.setStyle(LABEL_STYLE);

        Label value = new Label(item.value);
        String colour = switch (item.level) {
            case WARN -> "#b45309";
            case GOOD -> "#15803d";
            default -> "#0f172a";
        };
        value.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + colour + ";");

        HBox box = new HBox(8, label, value);
        box.setAlignment(Pos.BASELINE_LEFT);
        return box;
    }
}
