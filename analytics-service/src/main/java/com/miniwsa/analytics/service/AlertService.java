package com.miniwsa.analytics.service;

import com.miniwsa.analytics.alert.AlertDefinition;
import com.miniwsa.analytics.dto.request.DefineAlertRequest;
import com.miniwsa.analytics.dto.response.AlertDefinitionResponse;
import com.miniwsa.analytics.dto.response.AlertEvaluationResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.contracts.AttackCategory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertService {

    private static final Set<String> METRICS = Set.of("event_count", "unique_ips");
    private static final Map<String, Duration> WINDOWS = Map.of(
            "5m", Duration.ofMinutes(5),
            "1h", Duration.ofHours(1));

    private final Map<String, AlertDefinition> store = new ConcurrentHashMap<>();
    private final SecurityEventRepository repository;
    private final Clock clock;

    public AlertService(SecurityEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AlertDefinitionResponse define(DefineAlertRequest request) {
        if (!METRICS.contains(request.getMetric())) {
            throw new IllegalArgumentException("metric must be event_count or unique_ips");
        }
        if (!WINDOWS.containsKey(request.getWindow())) {
            throw new IllegalArgumentException("window must be 5m or 1h");
        }
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
                request.getMetric(),
                request.getWindow(),
                request.getThreshold());
        store.put(definition.getId(), definition);
        return toResponse(definition);
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
        Instant since = clock.instant().minus(WINDOWS.get(definition.getWindow()));
        long value;
        if ("unique_ips".equals(definition.getMetric())) {
            value = repository.countDistinctIpsSince(definition.getConfigId(), since);
        } else {
            value = repository.countSince(definition.getConfigId(), definition.getCategory(), since);
        }
        return new AlertEvaluationResponse(alertId, value >= definition.getThreshold(), value, definition.getThreshold());
    }

    private static AlertDefinitionResponse toResponse(AlertDefinition definition) {
        return new AlertDefinitionResponse(
                definition.getId(),
                definition.getConfigId(),
                definition.getCategory(),
                definition.getMetric(),
                definition.getWindow(),
                definition.getThreshold());
    }
}
