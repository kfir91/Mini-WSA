package com.wsa.analytics.exception;

import com.wsa.contracts.ApiErrors;
import com.wsa.contracts.ErrorDetail;
import com.wsa.contracts.ErrorReason;

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
