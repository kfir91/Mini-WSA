package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
import com.miniwsa.analytics.api.DefineAlertRequest;
import com.miniwsa.analytics.domain.AlertDefinition;
import com.miniwsa.analytics.domain.AlertMetric;
import com.miniwsa.analytics.domain.AlertWindow;
import com.miniwsa.analytics.mapper.AlertMapper;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.repository.SecurityEventSpecifications;
import com.miniwsa.contracts.AttackCategory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines and evaluates in-memory alert rules against stored events.
 */
@Service
public class AlertService {

    private final Map<String, AlertDefinition> store = new ConcurrentHashMap<>();
    private final SecurityEventRepository repository;
    private final AlertMapper alertMapper;
    private final Clock clock;

    public AlertService(SecurityEventRepository repository, AlertMapper alertMapper, Clock clock) {
        this.repository = repository;
        this.alertMapper = alertMapper;
        this.clock = clock;
    }

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
        store.put(definition.getId(), definition);
        return alertMapper.toResponse(definition);
    }

    public List<AlertEvaluationResponse> evaluateAll() {
        return store.keySet().stream().map(this::evaluateOne).toList();
    }

    public AlertEvaluationResponse evaluate(String alertId) {
        return evaluateOne(alertId);
    }

    private AlertEvaluationResponse evaluateOne(String alertId) {
        AlertDefinition definition = store.get(alertId);
        if (definition == null) {
            throw new IllegalStateException("Unknown alert id");
        }
        Instant since = clock.instant().minus(definition.getWindow().getDuration());
        long value;
        if (definition.getMetric() == AlertMetric.UNIQUE_IPS) {
            value = repository.countDistinctClientIps(
                    SecurityEventSpecifications.withFilters(
                            definition.getConfigId(), since, null, null, null, null));
        } else {
            value = repository.count(SecurityEventSpecifications.withFilters(
                    definition.getConfigId(), since, null, definition.getCategory(), null, null));
        }
        return new AlertEvaluationResponse(
                alertId, value >= definition.getThreshold(), value, definition.getThreshold());
    }
}
