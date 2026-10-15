package com.miniwsa.analytics.service;

import com.miniwsa.analytics.manager.EnrichmentManager;
import com.miniwsa.analytics.mapper.SecurityEventMapper;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.contracts.SecurityEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Classifies, scores, and stores Kafka security events idempotently by eventId.
 */
@Service
@RequiredArgsConstructor
public class EventEnrichmentService {

    private final SecurityEventRepository repository;
    private final EnrichmentManager enrichmentManager;
    private final SecurityEventMapper securityEventMapper;

    @Value("${miniwsa.enrichment.repeat-offender-window}")
    private Duration repeatOffenderWindow;

    @Transactional
    public void enrichAndStore(SecurityEventMessage message) {
        if (repository.existsById(message.getEventId())) {
            return;
        }
        Instant windowStart = message.getTimestamp().minus(repeatOffenderWindow);
        long prior = repository.countByClientIpAndEventTimestampGreaterThanEqual(message.getClientIp(), windowStart);
        String attackType = enrichmentManager.classify(message.getRule().getCategory());
        int score = enrichmentManager.threatScore(
                message.getRule().getSeverity(),
                message.getAction(),
                message.getPath(),
                prior);
        try {
            repository.save(securityEventMapper.toEntity(message, attackType, score));
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }
    }
}
