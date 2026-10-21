package com.wsa.analytics.service;

import com.wsa.analytics.api.AlertDefinitionResponse;
import com.wsa.analytics.api.AlertEvaluationResponse;
import com.wsa.analytics.api.DefineAlertRequest;
import com.wsa.analytics.domain.AlertDefinition;
import com.wsa.analytics.domain.AlertMetric;
import com.wsa.analytics.domain.AlertWindow;
import com.wsa.analytics.entity.AlertDefinitionEntity;
import com.wsa.analytics.mapper.AlertMapper;
import com.wsa.analytics.repository.AlertDefinitionRepository;
import com.wsa.analytics.repository.SecurityEventRepository;
import com.wsa.analytics.repository.SecurityEventSpecifications;
import com.wsa.contracts.ApiException;
import com.wsa.contracts.AttackCategory;
import com.wsa.contracts.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Defines and evaluates persisted alert rules against stored events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertDefinitionRepository alertDefinitionRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AlertMapper alertMapper;
    private final Clock clock;

    @Transactional
    public AlertDefinitionResponse define(DefineAlertRequest request) {
        AlertMetric metric = AlertMetric.fromWire(request.getMetric().getValue());
        AlertWindow window = AlertWindow.fromWire(request.getWindow().getValue());
        String category = request.getCategory();
        if (category != null && !category.isBlank()) {
            try {
                AttackCategory.valueOf(category);
            } catch (IllegalArgumentException ex) {
                throw ApiException.validation(ErrorMessage.INVALID_CATEGORY);
            }
        } else {
            category = null;
        }
        String ruleKey = AlertDefinition.ruleKey(
                request.getConfigId(), category, metric, window, request.getThreshold());
        if (alertDefinitionRepository.existsByRuleKey(ruleKey)) {
            throw ApiException.validation(ErrorMessage.DUPLICATE_ALERT_RULE);
        }
        AlertDefinition definition = AlertDefinition.builder()
                .id(UUID.randomUUID().toString())
                .configId(request.getConfigId())
                .category(category)
                .metric(metric)
                .window(window)
                .threshold(request.getThreshold())
                .build();
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
                .orElseThrow(() -> ApiException.notFound(ErrorMessage.UNKNOWN_ALERT));
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
        return alertMapper.toEvaluationResponse(definition, value);
    }
}
