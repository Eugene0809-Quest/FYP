package com.questiu.scheduler.model;

import java.time.LocalDate;

/** A gazetted public holiday (Employment Act 1955 premium-pay day, supervisor item #4). */
public class PublicHoliday {
    private final int holidayId;
    private final LocalDate date;
    private final String description;

    public PublicHoliday(int holidayId, LocalDate date, String description) {
        this.holidayId = holidayId;
        this.date = date;
        this.description = description;
    }

    public int getHolidayId() { return holidayId; }
    public LocalDate getDate() { return date; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return date + " - " + description;
    }
}
