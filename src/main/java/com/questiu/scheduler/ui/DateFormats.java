package com.questiu.scheduler.ui;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shows dates as day/month/year (e.g. 02/11/2026) in date pickers, which is
 * the order Malaysian users expect. Only the display changes: the value
 * stored in the database is still a real date.
 */
public final class DateFormats {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateFormats() {}

    public static StringConverter<LocalDate> dayMonthYear() {
        return new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DMY.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return null;
                try {
                    return LocalDate.parse(text.trim(), DMY);
                } catch (DateTimeParseException ex) {
                    return null;
                }
            }
        };
    }
}
