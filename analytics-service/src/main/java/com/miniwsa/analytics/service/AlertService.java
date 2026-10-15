package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
import com.miniwsa.analytics.api.DefineAlertRequest;
import com.miniwsa.analytics.domain.AlertDefinition;
import com.miniwsa.analytics.domain.AlertMetric;
import com.miniwsa.analytics.domain.AlertWindow;
import com.miniwsa.analytics.entity.AlertDefinitionEntity;
import com.miniwsa.analytics.mapper.AlertMapper;
import com.miniwsa.analytics.repository.AlertDefinitionRepository;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.repository.SecurityEventSpecifications;
import com.miniwsa.contracts.AttackCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Defines and evaluates persisted alert rules against stored events.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertDefinitionRepository alertDefinitionRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AlertMapper alertMapper;
    private final Clock clock;

    public AlertService(
            AlertDefinitionRepository alertDefinitionRepository,
            SecurityEventRepository securityEventRepository,
            AlertMapper alertMapper,
            Clock clock) {
        this.alertDefinitionRepository = alertDefinitionRepository;
        this.securityEventRepository = securityEventRepository;
        this.alertMapper = alertMapper;
        this.clock = clock;
    }

    @Transactional
    public AlertDefinitionResponse define(DefineAlertRequest request) {
        AlertMetric metric = AlertMetric.fromWire(request.getMetric());
        AlertWindow window = AlertWindow.fromWire(request.getWindow());
        String category = request.getCategory();
        if (category != null && !category.isBlank()) {
            try {
                AttackCategory.valueOf(category);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("category must be a valid attack category");
            }
        } else {
            category = null;
        }
        AlertDefinition definition = new AlertDefinition(
                UUID.randomUUID().toString(),
                request.getConfigId(),
                category,
                metric,
                window,
                request.getThreshold());
        AlertDefinitionEntity saved = alertDefinitionRepository.save(alertMapper.toEntity(definition, clock.instant()));
        log.info("Defined alert {}", saved.getId());
        return alertMapper.toResponse(alertMapper.toDomain(saved));
    }

    @Transactional(readOnly = true)
    public List<AlertEvaluationResponse> evaluateAll() {
        return alertDefinitionRepository.findAll().stream()
                .map(alertMapper::toDomain)
                .map(this::evaluateDefinition)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertEvaluationResponse evaluate(String alertId) {
        AlertDefinition definition = alertDefinitionRepository.findById(alertId)
                .map(alertMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Unknown alert id"));
        return evaluateDefinition(definition);
    }

    private AlertEvaluationResponse evaluateDefinition(AlertDefinition definition) {
        Instant since = clock.instant().minus(definition.getWindow().getDuration());
        long value;
        if (definition.getMetric() == AlertMetric.UNIQUE_IPS) {
            value = securityEventRepository.countDistinctClientIps(
                    SecurityEventSpecifications.withFilters(
                            definition.getConfigId(), since, null, null, null, null));
        } else {
            value = securityEventRepository.count(SecurityEventSpecifications.withFilters(
                    definition.getConfigId(), since, null, definition.getCategory(), null, null));
        }
        return new AlertEvaluationResponse(
                definition.getId(), value >= definition.getThreshold(), value, definition.getThreshold());
    }
}
