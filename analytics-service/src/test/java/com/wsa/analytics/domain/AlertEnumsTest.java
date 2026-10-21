package com.wsa.analytics.domain;

import com.wsa.contracts.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertEnumsTest {

    @Test
    void metricFromWireHappyAndInvalid() {
        assertEquals(AlertMetric.EVENT_COUNT, AlertMetric.fromWire("event_count"));
        assertThrows(ApiException.class, () -> AlertMetric.fromWire("nope"));
    }

    @Test
    void windowFromWireHappyAndInvalid() {
        assertEquals(AlertWindow.FIVE_MINUTES, AlertWindow.fromWire("5m"));
        assertThrows(ApiException.class, () -> AlertWindow.fromWire("2m"));
    }
}
