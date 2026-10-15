package com.miniwsa.ingest.exception;

import com.miniwsa.contracts.ApiErrors;
import com.miniwsa.contracts.ErrorDetail;
import com.miniwsa.contracts.ErrorReason;

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
