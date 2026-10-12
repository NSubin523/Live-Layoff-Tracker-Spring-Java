package com.example.layoff_tracker.model;

public enum LayoffStatus {
    CONFIRMED,
    RUMORED,
    UNKNOWN;

    public static LayoffStatus fromString(String value) {
        if (value == null) return UNKNOWN;
        try {
            return LayoffStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
