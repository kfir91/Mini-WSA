package com.miniwsa.ingest.exception;

import com.miniwsa.contracts.ApiException;
import com.miniwsa.contracts.ErrorDetail;
import com.miniwsa.contracts.ErrorMessage;

import java.util.List;

/**
 * Ingest validation failure; maps to OpenAPI {@code VALIDATION_ERROR}.
 */
public class IngestValidationException extends ApiException {

    public IngestValidationException(List<ErrorDetail> details) {
        super(
                com.miniwsa.contracts.ErrorCode.VALIDATION_ERROR,
                ErrorMessage.VALIDATION_FAILED,
                400,
                details);
    }
}
