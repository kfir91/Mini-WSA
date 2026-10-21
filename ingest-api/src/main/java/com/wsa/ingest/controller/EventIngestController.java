package com.wsa.ingest.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.contracts.ErrorReason;
import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.api.EventsApi;
import com.wsa.ingest.exception.ErrorDetails;
import com.wsa.ingest.exception.IngestValidationException;
import com.wsa.ingest.exception.ThrowableMessages;
import com.wsa.ingest.service.EventIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP adapter for event ingest; publishes to Kafka via {@link EventIngestService}.
 */
@RestController
@RequiredArgsConstructor
public class EventIngestController implements EventsApi {

    private final EventIngestService eventIngestService;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<Void> ingestEvents(JsonNode body) throws Exception {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new IngestValidationException(List.of(
                    ErrorDetails.of(null, "body", ErrorReason.BODY_REQUIRED)));
        }
        List<SecurityEventMessage> events = new ArrayList<>();
        if (body.isArray()) {
            if (body.isEmpty()) {
                throw new IngestValidationException(List.of(
                        ErrorDetails.of(null, "body", ErrorReason.EVENT_ARRAY_EMPTY)));
            }
            int index = 0;
            for (JsonNode node : body) {
                events.add(parseEvent(node, index++));
            }
        } else if (body.isObject()) {
            events.add(parseEvent(body, null));
        } else {
            throw new IngestValidationException(List.of(
                    ErrorDetails.of(null, "body", ErrorReason.BODY_NOT_OBJECT_OR_ARRAY)));
        }
        eventIngestService.ingest(events);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private SecurityEventMessage parseEvent(JsonNode node, Integer index) {
        try {
            return objectMapper.treeToValue(node, SecurityEventMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IngestValidationException(List.of(
                    ErrorDetails.of(
                            index,
                            "body",
                            ThrowableMessages.rootMessageOr(ex, ErrorReason.INVALID_EVENT_JSON.getValue()))));
        }
    }
}
