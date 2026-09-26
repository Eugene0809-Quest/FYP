package com.questiu.scheduler.dao;

/**
 * Maps between the smartshift schema's day_of_week ENUM('MON'..'SUN') and
 * the 1-7 (Monday=1 ... Sunday=7) integer representation used throughout
 * the model/solver layer (Shift, Availability, SchedulingEngine), so the
 * ENUM never has to leak past the DAO boundary.
 */
final class DayOfWeekMapper {

    private static final String[] CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    private DayOfWeekMapper() {}

    static int toInt(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equalsIgnoreCase(code)) return i + 1;
        }
        throw new IllegalArgumentException("Unknown day_of_week code: " + code);
    }

    static String toCode(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be 1-7, got: " + dayOfWeek);
        }
        return CODES[dayOfWeek - 1];
    }
}
