package com.miniwsa.analytics.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * Supported alert evaluation windows.
 */
@Getter
@RequiredArgsConstructor
public enum AlertWindow {
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    ONE_HOUR("1h", Duration.ofHours(1));

    private final String wireValue;
    private final Duration duration;

    public static AlertWindow fromWire(String value) {
        for (AlertWindow window : values()) {
            if (window.wireValue.equals(value)) {
                return window;
            }
        }
        throw new IllegalArgumentException("window must be 5m or 1h");
    }
}
