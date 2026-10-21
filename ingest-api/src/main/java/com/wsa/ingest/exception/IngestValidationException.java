package com.wsa.ingest.exception;

import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorDetail;
import com.wsa.contracts.ErrorMessage;

import java.util.List;

/**
 * Ingest validation failure; maps to OpenAPI {@code VALIDATION_ERROR}.
 */
public class IngestValidationException extends ApiException {

    public IngestValidationException(List<ErrorDetail> details) {
        super(
                com.wsa.contracts.ErrorCode.VALIDATION_ERROR,
                ErrorMessage.VALIDATION_FAILED,
                400,
                details);
    }
}
