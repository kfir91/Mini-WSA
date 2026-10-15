package com.miniwsa.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistence model for an enriched security event. Analytics-owned; not an API type.
 */
@Entity
@Table(name = "security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEventEntity {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "client_ip", nullable = false)
    private String clientIp;

    @Column(name = "hostname", nullable = false)
    private String hostname;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "rule_message", nullable = false)
    private String ruleMessage;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "request_size", nullable = false)
    private Integer requestSize;

    @Column(name = "response_size", nullable = false)
    private Integer responseSize;

    @Column(name = "attack_type", nullable = false)
    private String attackType;

    @Column(name = "threat_score", nullable = false)
    private Integer threatScore;
}
