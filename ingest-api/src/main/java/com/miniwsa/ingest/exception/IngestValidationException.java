package com.miniwsa.ingest.exception;

import com.miniwsa.ingest.api.ErrorDetail;

import java.util.List;

public class IngestValidationException extends RuntimeException {

    private final List<ErrorDetail> details;

    public IngestValidationException(List<ErrorDetail> details) {
        super("Validation failed");
        this.details = details;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
