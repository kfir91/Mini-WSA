package com.miniwsa.analytics.dto.response;

public class AlertDefinitionResponse {

    private final String id;
    private final Long configId;
    private final String category;
    private final String metric;
    private final String window;
    private final int threshold;

    public AlertDefinitionResponse(String id, Long configId, String category, String metric, String window, int threshold) {
        this.id = id;
        this.configId = configId;
        this.category = category;
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

    public String getCategory() {
        return category;
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
