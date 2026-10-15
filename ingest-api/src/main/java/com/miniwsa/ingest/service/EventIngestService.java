package com.miniwsa.ingest.service;

import com.miniwsa.contracts.ErrorReason;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.contracts.ErrorDetail;
import com.miniwsa.ingest.client.KafkaEventProducer;
import com.miniwsa.ingest.exception.ErrorDetails;
import com.miniwsa.ingest.exception.IngestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates wire events, stamps {@code receivedAt}, and publishes to Kafka.
 */
@Service
@RequiredArgsConstructor
public class EventIngestService {

    private final Validator validator;
    private final KafkaEventProducer producer;

    public void ingest(List<SecurityEventMessage> events) {
        if (events == null || events.isEmpty()) {
            throw new IngestValidationException(List.of(
                    ErrorDetails.of(null, "body", ErrorReason.BODY_EMPTY)));
        }
        List<ErrorDetail> details = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            SecurityEventMessage event = events.get(i);
            if (event == null) {
                details.add(ErrorDetails.of(i, "body", ErrorReason.EVENT_NULL));
                continue;
            }
            Set<ConstraintViolation<SecurityEventMessage>> violations = validator.validate(event);
            for (ConstraintViolation<SecurityEventMessage> violation : violations) {
                details.add(ErrorDetails.of(i, violation.getPropertyPath().toString(), violation.getMessage()));
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
