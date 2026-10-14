package com.miniwsa.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventMessageTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void validFixturePassesValidation() {
        Set<ConstraintViolation<SecurityEventMessage>> violations = validator.validate(validEvent());
        assertTrue(violations.isEmpty());
    }

    @Test
    void missingRequiredFieldsFailValidation() {
        SecurityEventMessage event = new SecurityEventMessage();
        Set<ConstraintViolation<SecurityEventMessage>> violations = validator.validate(event);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("eventId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("timestamp")));
    }

    @Test
    void invalidCategoryFailsDeserialization() {
        String json = validJson().replace("\"INJECTION\"", "\"NOT_A_CATEGORY\"");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void invalidActionFailsDeserialization() {
        String json = validJson().replace("\"DENY\"", "\"DROP\"");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void invalidSeverityFailsDeserialization() {
        String json = validJson().replace("\"CRITICAL\"", "\"EXTREME\"");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void badTimestampFailsDeserialization() {
        String json = validJson().replace("2026-05-20T14:32:10Z", "not-a-timestamp");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    private static SecurityEventMessage validEvent() {
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
        event.setEventId("evt-00132");
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

    private static String validJson() {
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
