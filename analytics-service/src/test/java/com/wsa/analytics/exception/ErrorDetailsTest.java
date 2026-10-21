package com.wsa.analytics.exception;

import com.wsa.contracts.ErrorDetail;
import com.wsa.contracts.ErrorReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorDetailsTest {

    @Test
    void ofReasonUsesCatalog() {
        ErrorDetail detail = ErrorDetails.of("metric", ErrorReason.BODY_EMPTY);
        assertEquals("metric", detail.getField());
        assertEquals(ErrorReason.BODY_EMPTY.getValue(), detail.getReason());
    }

    @Test
    void ofStringReasonPassesThrough() {
        ErrorDetail detail = ErrorDetails.of("window", "custom-reason");
        assertEquals("window", detail.getField());
        assertEquals("custom-reason", detail.getReason());
    }
}
