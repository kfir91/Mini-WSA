package com.miniwsa.analytics.dto.response;

public class AlertEvaluationResponse {

    private final String alertId;
    private final boolean firing;
    private final long value;
    private final int threshold;

    public AlertEvaluationResponse(String alertId, boolean firing, long value, int threshold) {
        this.alertId = alertId;
        this.firing = firing;
        this.value = value;
        this.threshold = threshold;
    }

    public String getAlertId() {
        return alertId;
    }

    public boolean isFiring() {
        return firing;
    }

    public long getValue() {
        return value;
    }

    public int getThreshold() {
        return threshold;
    }
}
