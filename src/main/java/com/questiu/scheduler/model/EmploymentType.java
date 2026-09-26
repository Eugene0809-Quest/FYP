package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * FULL_TIME / PART_TIME classification (added per supervisor feedback:
 * employee registration should capture full/part-time, which drives a
 * default max weekly hours). The registration UI pre-fills this default
 * when a type is selected, but the manager can still override it.
 */
public enum EmploymentType {
    FULL_TIME(new BigDecimal("45.00")),
    PART_TIME(new BigDecimal("30.00"));

    private final BigDecimal defaultMaxWeeklyHours;

    EmploymentType(BigDecimal defaultMaxWeeklyHours) {
        this.defaultMaxWeeklyHours = defaultMaxWeeklyHours;
    }

    public BigDecimal defaultMaxWeeklyHours() {
        return defaultMaxWeeklyHours;
    }
}
