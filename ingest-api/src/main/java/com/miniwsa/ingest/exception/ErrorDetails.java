package com.miniwsa.ingest.exception;

import com.miniwsa.ingest.api.ErrorDetail;

/**
 * Factory for OpenAPI {@link ErrorDetail} instances used across controller and service.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    public static ErrorDetail of(Integer index, String field, String reason) {
        ErrorDetail detail = new ErrorDetail();
        detail.setIndex(index);
        detail.setField(field);
        detail.setReason(reason);
        return detail;
    }
}
