package com.wsa.analytics.support;

import com.wsa.analytics.entity.SecurityEventEntity;

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
        return SecurityEventEntity.builder()
                .eventId(id)
                .eventTimestamp(ts)
                .receivedAt(ts.plusSeconds(1))
                .configId(configId)
                .policyId("pol_web1")
                .clientIp(clientIp)
                .hostname("www.example.com")
                .path(path)
                .method("POST")
                .statusCode(403)
                .userAgent("Mozilla/5.0")
                .ruleId("950001")
                .ruleName("SQL_INJECTION")
                .ruleMessage("detected")
                .severity("CRITICAL")
                .category(category)
                .action(action)
                .country("CN")
                .city("Beijing")
                .requestSize(1024)
                .responseSize(256)
                .attackType("SQL/Command Injection")
                .threatScore(score)
                .build();
    }
}
