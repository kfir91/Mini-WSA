package com.miniwsa.ingest.exception;

import com.miniwsa.contracts.ApiErrors;
import com.miniwsa.contracts.ApiException;
import com.miniwsa.contracts.ErrorCode;
import com.miniwsa.contracts.ErrorMessage;
import com.miniwsa.contracts.ErrorReason;
import com.miniwsa.contracts.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ex.toResponse());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable ingest payload");
        return ResponseEntity.badRequest()
                .body(ApiErrors.response(
                        ErrorCode.VALIDATION_ERROR,
                        ErrorMessage.BODY_UNREADABLE,
                        List.of(ApiErrors.detail(
                                null,
                                "body",
                                ThrowableMessages.rootMessageOr(ex, ErrorReason.INVALID_EVENT_JSON.getValue())))));
    }

    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<ErrorResponse> handlePublish(EventPublishException ex) {
        log.error("Kafka publish failed");
        return ResponseEntity.status(ApiException.brokerUnavailable().getHttpStatus())
                .body(ApiException.brokerUnavailable().toResponse());
    }
}
