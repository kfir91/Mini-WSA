package com.miniwsa.ingest.dto;

import java.util.List;

public class IngestErrorResponse {

    private final String code;
    private final String message;
    private final List<ErrorDetail> details;

    public IngestErrorResponse(String code, String message, List<ErrorDetail> details) {
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
