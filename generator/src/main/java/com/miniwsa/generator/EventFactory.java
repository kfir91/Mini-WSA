package com.miniwsa.generator;

import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.GeoLocationMessage;
import com.miniwsa.contracts.RuleMessage;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.contracts.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EventFactory {

    private static final List<String> PATHS = List.of("/api/v1/login", "/admin/users", "/search", "/checkout");
    private static final List<String> IPS = List.of("203.0.113.42", "198.51.100.23", "192.0.2.15", "203.0.113.88");

    public SecurityEventMessage create(String wave, long configId) {
        AttackCategory category = categoryFor(wave);
        RuleMessage rule = ruleFor(category);
        GeoLocationMessage geo = new GeoLocationMessage();
        geo.setCountry("CN");
        geo.setCity("Beijing");
        SecurityEventMessage event = new SecurityEventMessage();
        event.setEventId("evt-" + UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setConfigId(configId);
        event.setPolicyId("pol_web1");
        event.setClientIp(pick(IPS));
        event.setHostname("www.example.com");
        event.setPath(pick(PATHS));
        event.setMethod("POST");
        event.setStatusCode(403);
        event.setUserAgent("MiniWSA-Generator/1.0");
        event.setRule(rule);
        event.setAction(Action.DENY);
        event.setGeoLocation(geo);
        event.setRequestSize(1024);
        event.setResponseSize(256);
        return event;
    }

    private static AttackCategory categoryFor(String wave) {
        return switch (wave) {
            case "sql" -> AttackCategory.INJECTION;
            case "xss" -> AttackCategory.XSS;
            case "mixed" -> AttackCategory.values()[ThreadLocalRandom.current().nextInt(AttackCategory.values().length)];
            default -> throw new IllegalArgumentException("wave must be sql, xss, or mixed");
        };
    }

    private static RuleMessage ruleFor(AttackCategory category) {
        RuleMessage rule = new RuleMessage();
        rule.setCategory(category);
        switch (category) {
            case INJECTION -> {
                rule.setId("950001");
                rule.setName("SQL_INJECTION");
                rule.setMessage("SQL Injection Attack Detected");
                rule.setSeverity(Severity.CRITICAL);
            }
            case XSS -> {
                rule.setId("941100");
                rule.setName("XSS");
                rule.setMessage("XSS Attack Detected");
                rule.setSeverity(Severity.HIGH);
            }
            default -> {
                rule.setId("990000");
                rule.setName(category.name());
                rule.setMessage(category.name() + " detected");
                rule.setSeverity(Severity.MEDIUM);
            }
        }
        return rule;
    }

    private static String pick(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
