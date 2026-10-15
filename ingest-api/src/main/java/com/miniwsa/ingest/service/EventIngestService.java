package com.miniwsa.ingest.service;

import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.client.KafkaEventProducer;
import com.miniwsa.ingest.dto.ErrorDetail;
import com.miniwsa.ingest.exception.IngestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class EventIngestService {

    private final Validator validator;
    private final KafkaEventProducer producer;

    public EventIngestService(Validator validator, KafkaEventProducer producer) {
        this.validator = validator;
        this.producer = producer;
    }

    public void ingest(List<SecurityEventMessage> events) {
        if (events == null || events.isEmpty()) {
            throw new IngestValidationException(List.of(new ErrorDetail(null, "body", "Request body must contain an event or a non-empty array")));
        }
        List<ErrorDetail> details = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            SecurityEventMessage event = events.get(i);
            if (event == null) {
                details.add(new ErrorDetail(i, "body", "Event must not be null"));
                continue;
            }
            Set<ConstraintViolation<SecurityEventMessage>> violations = validator.validate(event);
            for (ConstraintViolation<SecurityEventMessage> violation : violations) {
                details.add(new ErrorDetail(i, violation.getPropertyPath().toString(), violation.getMessage()));
            }
        }
        if (!details.isEmpty()) {
            throw new IngestValidationException(details);
        }
        Instant receivedAt = Instant.now();
        for (SecurityEventMessage event : events) {
            event.setReceivedAt(receivedAt);
            producer.publish(event);
        }
    }
}
