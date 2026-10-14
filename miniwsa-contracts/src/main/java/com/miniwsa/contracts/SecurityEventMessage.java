package com.miniwsa.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class SecurityEventMessage {

    @NotBlank
    private String eventId;

    @NotNull
    private Instant timestamp;

    @NotNull
    private Long configId;

    @NotBlank
    private String policyId;

    @NotBlank
    private String clientIp;

    @NotBlank
    private String hostname;

    @NotBlank
    private String path;

    @NotBlank
    private String method;

    @NotNull
    private Integer statusCode;

    @NotBlank
    private String userAgent;

    @Valid
    @NotNull
    private RuleMessage rule;

    @NotNull
    private Action action;

    @Valid
    @NotNull
    private GeoLocationMessage geoLocation;

    @NotNull
    private Integer requestSize;

    @NotNull
    private Integer responseSize;

    private Instant receivedAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public RuleMessage getRule() {
        return rule;
    }

    public void setRule(RuleMessage rule) {
        this.rule = rule;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public GeoLocationMessage getGeoLocation() {
        return geoLocation;
    }

    public void setGeoLocation(GeoLocationMessage geoLocation) {
        this.geoLocation = geoLocation;
    }

    public Integer getRequestSize() {
        return requestSize;
    }

    public void setRequestSize(Integer requestSize) {
        this.requestSize = requestSize;
    }

    public Integer getResponseSize() {
        return responseSize;
    }

    public void setResponseSize(Integer responseSize) {
        this.responseSize = responseSize;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
