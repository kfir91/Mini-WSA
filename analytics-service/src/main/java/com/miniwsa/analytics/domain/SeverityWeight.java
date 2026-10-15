package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.Severity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Domain score points for a wire {@link Severity}. Kept out of contracts.
 */
public enum SeverityWeight {

    CRITICAL(Severity.CRITICAL, 40),
    HIGH(Severity.HIGH, 30),
    MEDIUM(Severity.MEDIUM, 20),
    LOW(Severity.LOW, 10);

    private static final Map<Severity, SeverityWeight> BY_SEVERITY = new EnumMap<>(Severity.class);

    static {
        for (SeverityWeight weight : values()) {
            BY_SEVERITY.put(weight.severity, weight);
        }
    }

    private final Severity severity;
    private final int points;

    SeverityWeight(Severity severity, int points) {
        this.severity = severity;
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    public static SeverityWeight from(Severity severity) {
        SeverityWeight weight = BY_SEVERITY.get(severity);
        if (weight == null) {
            throw new IllegalArgumentException("Unknown severity: " + severity);
        }
        return weight;
    }
}
