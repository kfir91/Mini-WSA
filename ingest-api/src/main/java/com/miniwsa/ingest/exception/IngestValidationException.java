package com.miniwsa.ingest.exception;

import com.miniwsa.ingest.api.ErrorDetail;
import lombok.Getter;

import java.util.List;

@Getter
public class IngestValidationException extends RuntimeException {

    private final List<ErrorDetail> details;

    public IngestValidationException(List<ErrorDetail> details) {
        super("Validation failed");
        this.details = details;
    }
}
