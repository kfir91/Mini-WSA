package com.miniwsa.analytics.service;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.manager.EnrichmentManager;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.contracts.SecurityEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class EventEnrichmentService {

    private final SecurityEventRepository repository;
    private final EnrichmentManager enrichmentManager;
    private final Duration repeatOffenderWindow;

    public EventEnrichmentService(
            SecurityEventRepository repository,
            EnrichmentManager enrichmentManager,
            @Value("${miniwsa.enrichment.repeat-offender-window}") Duration repeatOffenderWindow) {
        this.repository = repository;
        this.enrichmentManager = enrichmentManager;
        this.repeatOffenderWindow = repeatOffenderWindow;
    }

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
        repository.save(toEntity(message, attackType, score));
    }

    private static SecurityEventEntity toEntity(SecurityEventMessage message, String attackType, int score) {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setEventId(message.getEventId());
        entity.setEventTimestamp(message.getTimestamp());
        entity.setReceivedAt(message.getReceivedAt() != null ? message.getReceivedAt() : Instant.now());
        entity.setConfigId(message.getConfigId());
        entity.setPolicyId(message.getPolicyId());
        entity.setClientIp(message.getClientIp());
        entity.setHostname(message.getHostname());
        entity.setPath(message.getPath());
        entity.setMethod(message.getMethod());
        entity.setStatusCode(message.getStatusCode());
        entity.setUserAgent(message.getUserAgent());
        entity.setRuleId(message.getRule().getId());
        entity.setRuleName(message.getRule().getName());
        entity.setRuleMessage(message.getRule().getMessage());
        entity.setSeverity(message.getRule().getSeverity().name());
        entity.setCategory(message.getRule().getCategory().name());
        entity.setAction(message.getAction().name());
        entity.setCountry(message.getGeoLocation().getCountry());
        entity.setCity(message.getGeoLocation().getCity());
        entity.setRequestSize(message.getRequestSize());
        entity.setResponseSize(message.getResponseSize());
        entity.setAttackType(attackType);
        entity.setThreatScore(score);
        return entity;
    }
}
