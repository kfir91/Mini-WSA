package com.miniwsa.ingest.exception;

import com.miniwsa.ingest.api.ErrorDetail;
import com.miniwsa.ingest.api.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IngestValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IngestValidationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable ingest payload");
        ErrorDetail detail = new ErrorDetail();
        detail.setField("body");
        detail.setReason(rootMessage(ex));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "VALIDATION_ERROR",
                        "Request body is invalid JSON or uses an unsupported value",
                        List.of(detail)));
    }

    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<ErrorResponse> handlePublish(EventPublishException ex) {
        log.error("Kafka publish failed");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("BROKER_UNAVAILABLE", "Event broker is unavailable", List.of()));
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
