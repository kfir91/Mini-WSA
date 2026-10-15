package com.miniwsa.contracts;

import java.util.List;

/**
 * Builds {@link ErrorResponse} / {@link ErrorDetail} from the OpenAPI error catalog.
 * Prefer {@link ErrorCode}, {@link ErrorMessage}, and {@link ErrorReason} over string literals.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static ErrorResponse response(ErrorCode code, ErrorMessage message) {
        return response(code, message, List.of());
    }

    public static ErrorResponse response(ErrorCode code, ErrorMessage message, List<ErrorDetail> details) {
        return ErrorResponse.builder()
                .code(code)
                .message(message.getValue())
                .details(details == null ? List.of() : details)
                .build();
    }

    public static ErrorDetail detail(Integer index, String field, ErrorReason reason) {
        return ErrorDetail.builder()
                .index(index)
                .field(field)
                .reason(reason.getValue())
                .build();
    }

    public static ErrorDetail detail(String field, ErrorReason reason) {
        return detail(null, field, reason);
    }

    /** For Bean Validation / Jackson root messages that are not in {@link ErrorReason}. */
    public static ErrorDetail detail(Integer index, String field, String reason) {
        return ErrorDetail.builder()
                .index(index)
                .field(field)
                .reason(reason)
                .build();
    }

    public static ErrorDetail detail(String field, String reason) {
        return detail(null, field, reason);
    }
}
