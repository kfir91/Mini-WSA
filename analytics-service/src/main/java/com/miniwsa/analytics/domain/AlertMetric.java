package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.ApiException;
import com.miniwsa.contracts.ErrorMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Supported alert metrics.
 */
@Getter
@RequiredArgsConstructor
public enum AlertMetric {
    EVENT_COUNT("event_count"),
    UNIQUE_IPS("unique_ips");

    private final String wireValue;

    public static AlertMetric fromWire(String value) {
        for (AlertMetric metric : values()) {
            if (metric.wireValue.equals(value)) {
                return metric;
            }
        }
        throw ApiException.validation(ErrorMessage.INVALID_METRIC);
    }
}
