package com.miniwsa.analytics.domain;

import java.time.Duration;

/**
 * Supported in-memory alert evaluation windows.
 */
public enum AlertWindow {
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    ONE_HOUR("1h", Duration.ofHours(1));

    private final String wireValue;
    private final Duration duration;

    AlertWindow(String wireValue, Duration duration) {
        this.wireValue = wireValue;
        this.duration = duration;
    }

    public String getWireValue() {
        return wireValue;
    }

    public Duration getDuration() {
        return duration;
    }

    public static AlertWindow fromWire(String value) {
        for (AlertWindow window : values()) {
            if (window.wireValue.equals(value)) {
                return window;
            }
        }
        throw new IllegalArgumentException("window must be 5m or 1h");
    }
}
