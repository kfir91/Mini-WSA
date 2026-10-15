package com.miniwsa.analytics.exception;

import com.miniwsa.contracts.ApiErrors;
import com.miniwsa.contracts.ApiException;
import com.miniwsa.contracts.ErrorCode;
import com.miniwsa.contracts.ErrorDetail;
import com.miniwsa.contracts.ErrorMessage;
import com.miniwsa.contracts.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ex.toResponse());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(ApiErrors.response(ErrorCode.NOT_FOUND, ErrorMessage.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrors.response(ErrorCode.VALIDATION_ERROR, ErrorMessage.BODY_UNREADABLE));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .code(ErrorCode.VALIDATION_ERROR)
                        .message(ex.getMessage())
                        .details(List.of())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorDetails.of(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiErrors.response(ErrorCode.VALIDATION_ERROR, ErrorMessage.REQUEST_INVALID, details));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(404)
                .body(ErrorResponse.builder()
                        .code(ErrorCode.NOT_FOUND)
                        .message(ex.getMessage())
                        .details(List.of())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected analytics error", ex);
        return ResponseEntity.status(500).body(ApiException.internalError().toResponse());
    }
}
