package com.miniwsa.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.miniwsa.contracts.support.ClasspathJson;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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
        String json = ClasspathJson.load("fixtures/security-event-invalid-category.json");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void invalidActionFailsDeserialization() {
        String json = ClasspathJson.load("fixtures/security-event-invalid-action.json");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void invalidSeverityFailsDeserialization() {
        String json = ClasspathJson.load("fixtures/security-event-invalid-severity.json");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    @Test
    void badTimestampFailsDeserialization() {
        String json = ClasspathJson.load("fixtures/security-event-bad-timestamp.json");
        assertThrows(Exception.class, () -> objectMapper.readValue(json, SecurityEventMessage.class));
    }

    private static SecurityEventMessage validEvent() {
        try {
            return objectMapper.readValue(
                    ClasspathJson.load("fixtures/security-event-valid.json"),
                    SecurityEventMessage.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
