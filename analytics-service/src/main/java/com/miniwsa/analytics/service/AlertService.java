package com.miniwsa.analytics.service;

import com.miniwsa.analytics.alert.AlertDefinition;
import com.miniwsa.analytics.dto.request.DefineAlertRequest;
import com.miniwsa.analytics.dto.response.AlertEvaluationResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    public AlertDefinition define(DefineAlertRequest request) {
        if (!METRICS.contains(request.getMetric())) {
            throw new IllegalArgumentException("metric must be event_count or unique_ips");
        }
        if (!WINDOWS.containsKey(request.getWindow())) {
            throw new IllegalArgumentException("window must be 5m or 1h");
        }
        AlertDefinition definition = new AlertDefinition(
                UUID.randomUUID().toString(),
                request.getConfigId(),
                request.getMetric(),
                request.getWindow(),
                request.getThreshold());
        store.put(definition.getId(), definition);
        return definition;
    }

    public AlertEvaluationResponse evaluate(String alertId) {
        AlertDefinition definition = store.get(alertId);
        if (definition == null) {
            throw new IllegalStateException("Unknown alert id");
        }
        Instant since = clock.instant().minus(WINDOWS.get(definition.getWindow()));
        long value = "unique_ips".equals(definition.getMetric())
                ? repository.countDistinctIpsSince(definition.getConfigId(), since)
                : repository.countSince(definition.getConfigId(), since);
        return new AlertEvaluationResponse(alertId, value >= definition.getThreshold(), value, definition.getThreshold());
    }
}
