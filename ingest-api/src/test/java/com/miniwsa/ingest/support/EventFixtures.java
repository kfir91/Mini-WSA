package com.miniwsa.ingest.support;

import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.GeoLocationMessage;
import com.miniwsa.contracts.RuleMessage;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.contracts.Severity;

import java.time.Instant;

public final class EventFixtures {

    private EventFixtures() {
    }

    public static SecurityEventMessage validEvent(String eventId) {
        RuleMessage rule = new RuleMessage();
        rule.setId("950001");
        rule.setName("SQL_INJECTION");
        rule.setMessage("SQL Injection Attack Detected");
        rule.setSeverity(Severity.CRITICAL);
        rule.setCategory(AttackCategory.INJECTION);

        GeoLocationMessage geo = new GeoLocationMessage();
        geo.setCountry("CN");
        geo.setCity("Beijing");

        SecurityEventMessage event = new SecurityEventMessage();
        event.setEventId(eventId);
        event.setTimestamp(Instant.parse("2026-05-20T14:32:10Z"));
        event.setConfigId(14227L);
        event.setPolicyId("pol_web1");
        event.setClientIp("203.0.113.42");
        event.setHostname("www.example.com");
        event.setPath("/api/v1/login");
        event.setMethod("POST");
        event.setStatusCode(403);
        event.setUserAgent("Mozilla/5.0");
        event.setRule(rule);
        event.setAction(Action.DENY);
        event.setGeoLocation(geo);
        event.setRequestSize(1024);
        event.setResponseSize(256);
        return event;
    }

    public static String validJson() {
        return """
                {
                  "eventId": "evt-00132",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 14227,
                  "policyId": "pol_web1",
                  "clientIp": "203.0.113.42",
                  "hostname": "www.example.com",
                  "path": "/api/v1/login",
                  "method": "POST",
                  "statusCode": 403,
                  "userAgent": "Mozilla/5.0",
                  "rule": {
                    "id": "950001",
                    "name": "SQL_INJECTION",
                    "message": "SQL Injection Attack Detected",
                    "severity": "CRITICAL",
                    "category": "INJECTION"
                  },
                  "action": "DENY",
                  "geoLocation": {
                    "country": "CN",
                    "city": "Beijing"
                  },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """;
    }
}
