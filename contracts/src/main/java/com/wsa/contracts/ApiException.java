package com.wsa.contracts;

import java.util.List;

/**
 * API failure carrying OpenAPI {@link ErrorCode} + {@link ErrorMessage} (and optional details).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final ErrorMessage errorMessage;
    private final int httpStatus;
    private final List<ErrorDetail> details;

    public ApiException(ErrorCode code, ErrorMessage errorMessage, int httpStatus, List<ErrorDetail> details) {
        super(errorMessage.getValue());
        this.code = code;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiException validation(ErrorMessage message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, 400, List.of());
    }

    public static ApiException validation(ErrorMessage message, List<ErrorDetail> details) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, 400, details);
    }

    public static ApiException notFound(ErrorMessage message) {
        return new ApiException(ErrorCode.NOT_FOUND, message, 404, List.of());
    }

    public static ApiException brokerUnavailable() {
        return new ApiException(ErrorCode.BROKER_UNAVAILABLE, ErrorMessage.BROKER_UNAVAILABLE, 503, List.of());
    }

    public static ApiException rateLimited() {
        return new ApiException(ErrorCode.RATE_LIMITED, ErrorMessage.TOO_MANY_REQUESTS, 429, List.of());
    }

    public static ApiException internalError() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, ErrorMessage.UNEXPECTED_ERROR, 500, List.of());
    }

    public ErrorCode getCode() {
        return code;
    }

    public ErrorMessage getErrorMessage() {
        return errorMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }

    public ErrorResponse toResponse() {
        return ApiErrors.response(code, errorMessage, details);
    }
}
