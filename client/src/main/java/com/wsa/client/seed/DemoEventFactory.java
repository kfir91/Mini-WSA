package com.wsa.client.seed;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds synthetic ingest payloads (same shape as the CLI generator) without a contracts dependency.
 */
final class DemoEventFactory {

    private static final List<String> PATHS = List.of("/api/v1/login", "/admin/users", "/search", "/checkout");
    private static final List<String> IPS = List.of("203.0.113.42", "198.51.100.23", "192.0.2.15", "203.0.113.88");
    private static final List<RuleTemplate> RULES = List.of(
            new RuleTemplate("950001", "SQL_INJECTION", "SQL Injection Attack Detected", "CRITICAL", "INJECTION"),
            new RuleTemplate("941100", "XSS", "XSS Attack Detected", "HIGH", "XSS"),
            new RuleTemplate("990000", "BOT", "BOT detected", "MEDIUM", "BOT"),
            new RuleTemplate("990000", "DOS", "DOS detected", "MEDIUM", "DOS"),
            new RuleTemplate("990000", "PROTOCOL_VIOLATION", "PROTOCOL_VIOLATION detected", "MEDIUM", "PROTOCOL_VIOLATION"),
            new RuleTemplate("990000", "DATA_LEAKAGE", "DATA_LEAKAGE detected", "MEDIUM", "DATA_LEAKAGE"),
            new RuleTemplate("990000", "RATE_LIMIT", "RATE_LIMIT detected", "MEDIUM", "RATE_LIMIT")
    );

    private final int burstSize;
    private String waveIp;
    private String wavePath;
    private int waveStartIndex = Integer.MIN_VALUE;

    DemoEventFactory(int burstSize) {
        this.burstSize = Math.max(1, burstSize);
    }

    Map<String, Object> create(int index, long configId) {
        boolean burst = (index % (burstSize * 2L)) < burstSize;
        String ip;
        String path;
        if (burst) {
            startWaveIfNeeded(index);
            ip = waveIp;
            path = wavePath;
        } else {
            ip = pick(IPS);
            path = pick(PATHS);
        }
        RuleTemplate rule = RULES.get(ThreadLocalRandom.current().nextInt(RULES.size()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", "evt-" + UUID.randomUUID());
        body.put("timestamp", Instant.now().toString());
        body.put("configId", configId);
        body.put("policyId", "pol_web1");
        body.put("clientIp", ip);
        body.put("hostname", "www.example.com");
        body.put("path", path);
        body.put("method", "POST");
        body.put("statusCode", 403);
        body.put("userAgent", "WSA-Hub-Seed/1.0");
        body.put("rule", Map.of(
                "id", rule.id(),
                "name", rule.name(),
                "message", rule.message(),
                "severity", rule.severity(),
                "category", rule.category()
        ));
        body.put("action", "DENY");
        body.put("geoLocation", Map.of("country", "CN", "city", "Beijing"));
        body.put("requestSize", 1024);
        body.put("responseSize", 256);
        return body;
    }

    private void startWaveIfNeeded(int index) {
        int waveIndex = index / burstSize;
        if (waveIp == null || waveStartIndex != waveIndex) {
            waveStartIndex = waveIndex;
            waveIp = pick(IPS);
            wavePath = pick(PATHS);
        }
    }

    private static String pick(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private record RuleTemplate(String id, String name, String message, String severity, String category) {
    }
}
