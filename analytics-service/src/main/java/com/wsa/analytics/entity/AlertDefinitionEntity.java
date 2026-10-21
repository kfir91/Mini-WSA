package com.wsa.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistence model for an alert rule definition. Analytics-owned; not an API type.
 */
@Entity
@Table(name = "alert_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(name = "rule_key", nullable = false, length = 128)
    private String ruleKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
