package com.miniwsa.analytics.alert;

public class AlertDefinition {

    private final String id;
    private final Long configId;
    private final String metric;
    private final String window;
    private final int threshold;

    public AlertDefinition(String id, Long configId, String metric, String window, int threshold) {
        this.id = id;
        this.configId = configId;
        this.metric = metric;
        this.window = window;
        this.threshold = threshold;
    }

    public String getId() {
        return id;
    }

    public Long getConfigId() {
        return configId;
    }

    public String getMetric() {
        return metric;
    }

    public String getWindow() {
        return window;
    }

    public int getThreshold() {
        return threshold;
    }
}
