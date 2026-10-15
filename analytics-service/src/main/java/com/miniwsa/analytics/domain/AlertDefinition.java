package com.miniwsa.analytics.domain;

/**
 * In-memory alert rule owned by analytics (not persisted).
 */
public class AlertDefinition {

    private final String id;
    private final Long configId;
    private final String category;
    private final AlertMetric metric;
    private final AlertWindow window;
    private final int threshold;

    public AlertDefinition(
            String id, Long configId, String category, AlertMetric metric, AlertWindow window, int threshold) {
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

    public AlertMetric getMetric() {
        return metric;
    }

    public AlertWindow getWindow() {
        return window;
    }

    public int getThreshold() {
        return threshold;
    }
}
