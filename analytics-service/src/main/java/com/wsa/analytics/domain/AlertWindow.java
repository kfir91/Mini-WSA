package com.wsa.analytics.domain;

import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorMessage;
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
        throw ApiException.validation(ErrorMessage.INVALID_WINDOW);
    }
}
