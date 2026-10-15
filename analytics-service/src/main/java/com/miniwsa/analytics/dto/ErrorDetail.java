package com.miniwsa.analytics.dto;

public class ErrorDetail {

    private final Integer index;
    private final String field;
    private final String reason;

    public ErrorDetail(Integer index, String field, String reason) {
        this.index = index;
        this.field = field;
        this.reason = reason;
    }

    public Integer getIndex() {
        return index;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
