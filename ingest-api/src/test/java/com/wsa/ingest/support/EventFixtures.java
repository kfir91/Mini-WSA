package com.wsa.ingest.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wsa.contracts.SecurityEventMessage;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class EventFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private EventFixtures() {
    }

    public static SecurityEventMessage validEvent(String eventId) {
        try {
            SecurityEventMessage event = MAPPER.readValue(validJson(), SecurityEventMessage.class);
            event.setEventId(eventId);
            return event;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String validJson() {
        return ClasspathJson.load("fixtures/security-event-valid.json");
    }
}
