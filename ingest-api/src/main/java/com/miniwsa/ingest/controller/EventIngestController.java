package com.miniwsa.ingest.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.api.ErrorDetail;
import com.miniwsa.ingest.api.EventsApi;
import com.miniwsa.ingest.exception.IngestValidationException;
import com.miniwsa.ingest.service.EventIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP adapter for event ingest; publishes to Kafka via {@link EventIngestService}.
 */
@RestController
public class EventIngestController implements EventsApi {

    private final EventIngestService eventIngestService;
    private final ObjectMapper objectMapper;

    public EventIngestController(EventIngestService eventIngestService, ObjectMapper objectMapper) {
        this.eventIngestService = eventIngestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Void> ingestEvents(JsonNode body) throws Exception {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new IngestValidationException(List.of(detail(null, "body", "Request body is required")));
        }
        List<SecurityEventMessage> events = new ArrayList<>();
        if (body.isArray()) {
            if (body.isEmpty()) {
                throw new IngestValidationException(List.of(detail(null, "body", "Event array must not be empty")));
            }
            int index = 0;
            for (JsonNode node : body) {
                events.add(parseEvent(node, index++));
            }
        } else if (body.isObject()) {
            events.add(parseEvent(body, null));
        } else {
            throw new IngestValidationException(List.of(detail(null, "body", "Body must be a JSON object or array")));
        }
        eventIngestService.ingest(events);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private SecurityEventMessage parseEvent(JsonNode node, Integer index) {
        try {
            return objectMapper.treeToValue(node, SecurityEventMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IngestValidationException(List.of(detail(index, "body", rootMessage(ex))));
        }
    }

    private static ErrorDetail detail(Integer index, String field, String reason) {
        ErrorDetail d = new ErrorDetail();
        d.setIndex(index);
        d.setField(field);
        d.setReason(reason);
        return d;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Invalid event JSON" : current.getMessage();
    }
}
