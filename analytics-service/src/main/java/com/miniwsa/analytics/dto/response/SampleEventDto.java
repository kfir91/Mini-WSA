package com.miniwsa.analytics.dto.response;

import java.time.Instant;

public class SampleEventDto {

    private final String eventId;
    private final Instant timestamp;
    private final Instant receivedAt;
    private final Long configId;
    private final String policyId;
    private final String clientIp;
    private final String hostname;
    private final String path;
    private final String method;
    private final Integer statusCode;
    private final String userAgent;
    private final String ruleId;
    private final String ruleName;
    private final String ruleMessage;
    private final String severity;
    private final String category;
    private final String action;
    private final String country;
    private final String city;
    private final Integer requestSize;
    private final Integer responseSize;
    private final String attackType;
    private final Integer threatScore;

    public SampleEventDto(
            String eventId,
            Instant timestamp,
            Instant receivedAt,
            Long configId,
            String policyId,
            String clientIp,
            String hostname,
            String path,
            String method,
            Integer statusCode,
            String userAgent,
            String ruleId,
            String ruleName,
            String ruleMessage,
            String severity,
            String category,
            String action,
            String country,
            String city,
            Integer requestSize,
            Integer responseSize,
            String attackType,
            Integer threatScore) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.receivedAt = receivedAt;
        this.configId = configId;
        this.policyId = policyId;
        this.clientIp = clientIp;
        this.hostname = hostname;
        this.path = path;
        this.method = method;
        this.statusCode = statusCode;
        this.userAgent = userAgent;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleMessage = ruleMessage;
        this.severity = severity;
        this.category = category;
        this.action = action;
        this.country = country;
        this.city = city;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
        this.attackType = attackType;
        this.threatScore = threatScore;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getConfigId() {
        return configId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getHostname() {
        return hostname;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleMessage() {
        return ruleMessage;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Integer getRequestSize() {
        return requestSize;
    }

    public Integer getResponseSize() {
        return responseSize;
    }

    public String getAttackType() {
        return attackType;
    }

    public Integer getThreatScore() {
        return threatScore;
    }
}
