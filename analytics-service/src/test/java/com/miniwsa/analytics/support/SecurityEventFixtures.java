package com.miniwsa.analytics.support;

import com.miniwsa.analytics.entity.SecurityEventEntity;

import java.time.Instant;

public final class SecurityEventFixtures {

    private SecurityEventFixtures() {
    }

    public static SecurityEventEntity event(
            String id, String category, String action, int score, Instant ts) {
        return event(id, 14227L, category, action, score, ts, "203.0.113.42", "/api/v1/login");
    }

    public static SecurityEventEntity event(
            String id,
            Long configId,
            String category,
            String action,
            int score,
            Instant ts,
            String clientIp,
            String path) {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setEventId(id);
        entity.setEventTimestamp(ts);
        entity.setReceivedAt(ts.plusSeconds(1));
        entity.setConfigId(configId);
        entity.setPolicyId("pol_web1");
        entity.setClientIp(clientIp);
        entity.setHostname("www.example.com");
        entity.setPath(path);
        entity.setMethod("POST");
        entity.setStatusCode(403);
        entity.setUserAgent("Mozilla/5.0");
        entity.setRuleId("950001");
        entity.setRuleName("SQL_INJECTION");
        entity.setRuleMessage("detected");
        entity.setSeverity("CRITICAL");
        entity.setCategory(category);
        entity.setAction(action);
        entity.setCountry("CN");
        entity.setCity("Beijing");
        entity.setRequestSize(1024);
        entity.setResponseSize(256);
        entity.setAttackType("SQL/Command Injection");
        entity.setThreatScore(score);
        return entity;
    }
}
