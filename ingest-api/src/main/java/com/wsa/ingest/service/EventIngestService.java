package com.wsa.ingest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.contracts.ErrorDetail;
import com.wsa.contracts.ErrorReason;
import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.api.IngestAcceptItem;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.exception.ErrorDetails;
import com.wsa.ingest.exception.IngestValidationException;
import com.wsa.ingest.repository.IngestOutboxRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates wire events, stamps {@code receivedAt}, and durably accepts into the outbox.
 * Kafka publish is performed asynchronously by {@link OutboxRelayService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestService {

    private final Validator validator;
    private final IngestOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<IngestAcceptItem> ingest(List<SecurityEventMessage> events) {
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
        List<IngestAcceptItem> accepted = new ArrayList<>(events.size());
        for (SecurityEventMessage event : events) {
            var existing = outboxRepository.findByEventId(event.getEventId());
            if (existing.isPresent()) {
                log.debug("Outbox already has eventId={}, treating as idempotent accept", event.getEventId());
                accepted.add(IngestAcceptItem.builder()
                        .eventId(event.getEventId())
                        .status(IngestStatusMapper.toClientStatus(existing.get()))
                        .build());
                continue;
            }
            event.setReceivedAt(receivedAt);
            outboxRepository.save(IngestOutboxEntity.builder()
                    .eventId(event.getEventId())
                    .payload(toJson(event))
                    .status(OutboxStatus.PENDING)
                    .createdAt(receivedAt)
                    .attemptCount(0)
                    .build());
            accepted.add(IngestAcceptItem.builder()
                    .eventId(event.getEventId())
                    .status(IngestStatusMapper.forNewPending())
                    .build());
        }
        return accepted;
    }

    private String toJson(SecurityEventMessage event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize security event for outbox", ex);
        }
    }
}
