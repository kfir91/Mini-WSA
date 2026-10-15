package com.miniwsa.analytics.exception;

import com.miniwsa.analytics.api.ErrorDetail;

/**
 * Factory for OpenAPI {@link ErrorDetail} used by the analytics exception handler.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    public static ErrorDetail of(String field, String reason) {
        ErrorDetail detail = new ErrorDetail();
        detail.setField(field);
        detail.setReason(reason);
        return detail;
    }
}
