package com.wsa.analytics.domain;

import com.wsa.contracts.Severity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domain score points for a wire {@link Severity}. Kept out of contracts.
 */
@Getter
@RequiredArgsConstructor
public enum SeverityWeight {

    CRITICAL(Severity.CRITICAL, 40),
    HIGH(Severity.HIGH, 30),
    MEDIUM(Severity.MEDIUM, 20),
    LOW(Severity.LOW, 10);

    private final Severity severity;
    private final int points;

    public static SeverityWeight from(Severity severity) {
        return switch (severity) {
            case CRITICAL -> CRITICAL;
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
        };
    }
}
