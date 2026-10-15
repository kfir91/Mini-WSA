package com.miniwsa.ingest.exception;

import com.miniwsa.ingest.dto.ErrorDetail;
import com.miniwsa.ingest.dto.IngestErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IngestValidationException.class)
    public ResponseEntity<IngestErrorResponse> handleValidation(IngestValidationException ex) {
        return ResponseEntity.badRequest()
                .body(new IngestErrorResponse("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<IngestErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable ingest payload");
        return ResponseEntity.badRequest()
                .body(new IngestErrorResponse(
                        "VALIDATION_ERROR",
                        "Request body is invalid JSON or uses an unsupported value",
                        List.of(new ErrorDetail(null, "body", rootMessage(ex)))));
    }

    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<IngestErrorResponse> handlePublish(EventPublishException ex) {
        log.error("Kafka publish failed");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new IngestErrorResponse("BROKER_UNAVAILABLE", "Event broker is unavailable", List.of()));
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
