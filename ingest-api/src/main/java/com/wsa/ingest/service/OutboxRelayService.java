package com.wsa.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.client.KafkaEventProducer;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.repository.IngestOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Polls PENDING outbox rows and publishes them to Kafka outside any DB transaction.
 * Claim uses {@code FOR UPDATE SKIP LOCKED}; publish runs after the claim TX commits.
 */
@Service
@Slf4j
public class OutboxRelayService {

    private final IngestOutboxRepository outboxRepository;
    private final KafkaEventProducer producer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${wsa.outbox.relay-enabled:true}")
    private boolean relayEnabled;

    @Value("${wsa.outbox.batch-size:50}")
    private int batchSize;

    @Value("${wsa.outbox.max-attempts:5}")
    private int maxAttempts;

    public OutboxRelayService(
            IngestOutboxRepository outboxRepository,
            KafkaEventProducer producer,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${wsa.outbox.poll-interval-ms:1000}")
    public void scheduledRelay() {
        if (!relayEnabled) {
            return;
        }
        relayBatch();
    }

    /**
     * Claims up to {@code batchSize} PENDING rows, then publishes each outside a DB TX.
     */
    public void relayBatch() {
        List<Long> claimedIds = transactionTemplate.execute(status ->
                outboxRepository.lockNextPending(batchSize).stream()
                        .map(IngestOutboxEntity::getId)
                        .toList());
        if (claimedIds.isEmpty()) {
            return;
        }
        for (Long id : claimedIds) {
            relayOne(id);
        }
    }

    private void relayOne(Long id) {
        ClaimedRow claimed = transactionTemplate.execute(status -> {
            IngestOutboxEntity row = outboxRepository.findById(id).orElse(null);
            if (row == null || row.getStatus() != OutboxStatus.PENDING) {
                return null;
            }
            return new ClaimedRow(row.getId(), row.getEventId(), row.getPayload(), row.getAttemptCount());
        });
        if (claimed == null) {
            return;
        }

        try {
            SecurityEventMessage message = objectMapper.readValue(claimed.payload(), SecurityEventMessage.class);
            producer.publish(message);
            transactionTemplate.executeWithoutResult(status -> markPublished(claimed.id()));
        } catch (Exception ex) {
            transactionTemplate.executeWithoutResult(status -> markFailure(claimed.id(), claimed.eventId(), claimed.attemptCount(), ex));
        }
    }

    private void markPublished(Long id) {
        IngestOutboxEntity row = outboxRepository.findById(id).orElse(null);
        if (row == null || row.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        row.setStatus(OutboxStatus.PUBLISHED);
        row.setPublishedAt(Instant.now());
        row.setLastError(null);
        outboxRepository.save(row);
    }

    private void markFailure(Long id, String eventId, int previousAttempts, Exception ex) {
        IngestOutboxEntity row = outboxRepository.findById(id).orElse(null);
        if (row == null || row.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        int attempts = previousAttempts + 1;
        row.setAttemptCount(attempts);
        row.setLastError(truncate(ex.getMessage()));
        if (attempts >= maxAttempts) {
            row.setStatus(OutboxStatus.FAILED);
            log.error("Outbox eventId={} marked FAILED after {} attempts", eventId, attempts);
        } else {
            log.warn("Outbox eventId={} publish failed attempt={}: {}", eventId, attempts, ex.getMessage());
        }
        outboxRepository.save(row);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    private record ClaimedRow(Long id, String eventId, String payload, int attemptCount) {
    }
}
