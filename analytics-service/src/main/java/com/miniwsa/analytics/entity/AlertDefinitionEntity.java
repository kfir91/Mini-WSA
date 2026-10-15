package com.miniwsa.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistence model for an alert rule definition. Analytics-owned; not an API type.
 */
@Entity
@Table(name = "alert_definitions")
public class AlertDefinitionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "config_id")
    private Long configId;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "metric", nullable = false, length = 32)
    private String metric;

    @Column(name = "eval_window", nullable = false, length = 8)
    private String window;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getWindow() {
        return window;
    }

    public void setWindow(String window) {
        this.window = window;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
