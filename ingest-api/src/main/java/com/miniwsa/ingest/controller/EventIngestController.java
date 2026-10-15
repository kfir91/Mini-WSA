package com.miniwsa.ingest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.dto.ErrorDetail;
import com.miniwsa.ingest.exception.IngestValidationException;
import com.miniwsa.ingest.service.EventIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class EventIngestController {

    private final EventIngestService eventIngestService;
    private final ObjectMapper objectMapper;

    public EventIngestController(EventIngestService eventIngestService, ObjectMapper objectMapper) {
        this.eventIngestService = eventIngestService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/events/ingest")
    public ResponseEntity<Void> ingest(@RequestBody(required = false) JsonNode body) throws Exception {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new IngestValidationException(List.of(new ErrorDetail(null, "body", "Request body is required")));
        }
        List<SecurityEventMessage> events = new ArrayList<>();
        if (body.isArray()) {
            if (body.isEmpty()) {
                throw new IngestValidationException(List.of(new ErrorDetail(null, "body", "Event array must not be empty")));
            }
            for (JsonNode node : body) {
                events.add(objectMapper.treeToValue(node, SecurityEventMessage.class));
            }
        } else if (body.isObject()) {
            events.add(objectMapper.treeToValue(body, SecurityEventMessage.class));
        } else {
            throw new IngestValidationException(List.of(new ErrorDetail(null, "body", "Body must be a JSON object or array")));
        }
        eventIngestService.ingest(events);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
