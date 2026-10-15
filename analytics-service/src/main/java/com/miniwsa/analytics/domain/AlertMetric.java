package com.miniwsa.analytics.domain;

/**
 * Supported alert metrics.
 */
public enum AlertMetric {
    EVENT_COUNT("event_count"),
    UNIQUE_IPS("unique_ips");

    private final String wireValue;

    AlertMetric(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static AlertMetric fromWire(String value) {
        for (AlertMetric metric : values()) {
            if (metric.wireValue.equals(value)) {
                return metric;
            }
        }
        throw new IllegalArgumentException("metric must be event_count or unique_ips");
    }
}
