package com.miniwsa.analytics.exception;

import com.miniwsa.contracts.ApiErrors;
import com.miniwsa.contracts.ErrorDetail;
import com.miniwsa.contracts.ErrorReason;

/**
 * Analytics-facing factory over the shared OpenAPI {@link ErrorDetail} catalog.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    public static ErrorDetail of(String field, ErrorReason reason) {
        return ApiErrors.detail(field, reason);
    }

    public static ErrorDetail of(String field, String reason) {
        return ApiErrors.detail(field, reason);
    }
}
