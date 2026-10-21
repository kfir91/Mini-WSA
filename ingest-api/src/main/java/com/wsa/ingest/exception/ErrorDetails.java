package com.wsa.ingest.exception;

import com.wsa.contracts.ApiErrors;
import com.wsa.contracts.ErrorDetail;
import com.wsa.contracts.ErrorReason;

/**
 * Ingest-facing factory over the shared OpenAPI {@link ErrorDetail} catalog.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    public static ErrorDetail of(Integer index, String field, ErrorReason reason) {
        return ApiErrors.detail(index, field, reason);
    }

    public static ErrorDetail of(Integer index, String field, String reason) {
        return ApiErrors.detail(index, field, reason);
    }
}
