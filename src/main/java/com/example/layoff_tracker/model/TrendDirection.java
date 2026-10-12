package com.example.layoff_tracker.model;

public enum TrendDirection {
    INCREASING,
    DECREASING,
    STABLE,
    UNKNOWN;

    public static TrendDirection fromString(String value) {
        if (value == null) return UNKNOWN;
        try {
            return TrendDirection.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
