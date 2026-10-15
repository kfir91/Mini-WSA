package com.miniwsa.analytics.dto;

import java.util.List;

public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<ErrorDetail> details;

    public ErrorResponse(String code, String message, List<ErrorDetail> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
