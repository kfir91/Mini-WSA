package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
import com.miniwsa.analytics.api.DefineAlertRequest;
import com.miniwsa.analytics.entity.AlertDefinitionEntity;
import com.miniwsa.analytics.mapper.AlertMapper;
import com.miniwsa.analytics.repository.AlertDefinitionRepository;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertDefinitionRepository alertDefinitionRepository;

    @Mock
    private SecurityEventRepository securityEventRepository;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T15:00:00Z"), ZoneOffset.UTC);
        alertService = new AlertService(
                alertDefinitionRepository,
                securityEventRepository,
                Mappers.getMapper(AlertMapper.class),
                clock);
        lenient().when(alertDefinitionRepository.save(any(AlertDefinitionEntity.class))).thenAnswer(invocation -> {
            AlertDefinitionEntity entity = invocation.getArgument(0);
            return entity;
        });
    }

    @Test
    void definePersistsAlert() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 3, null));
        ArgumentCaptor<AlertDefinitionEntity> captor = ArgumentCaptor.forClass(AlertDefinitionEntity.class);
        verify(alertDefinitionRepository).save(captor.capture());
        assertEquals(defined.getId(), captor.getValue().getId());
        assertEquals("event_count", captor.getValue().getMetric());
        assertEquals("5m", captor.getValue().getWindow());
        assertEquals(3, captor.getValue().getThreshold());
        assertEquals(Instant.parse("2026-05-20T15:00:00Z"), captor.getValue().getCreatedAt());
    }

    @Test
    void firesWhenEventCountMeetsThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 3, null));
        when(alertDefinitionRepository.findById(defined.getId())).thenReturn(Optional.of(entityFrom(defined)));
        when(securityEventRepository.count(any(Specification.class))).thenReturn(3L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.getFiring());
        assertEquals(3L, result.getValue());
    }

    @Test
    void countsCategoryWhenDefined() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 2, "INJECTION"));
        when(alertDefinitionRepository.findById(defined.getId())).thenReturn(Optional.of(entityFrom(defined)));
        when(securityEventRepository.count(any(Specification.class))).thenReturn(4L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.getFiring());
        assertEquals(4L, result.getValue());
    }

    @Test
    void evaluateAllReturnsEveryRule() {
        AlertDefinitionResponse first = alertService.define(request("event_count", 3, null));
        AlertDefinitionResponse second = alertService.define(request("unique_ips", 5, null));
        when(alertDefinitionRepository.findAll()).thenReturn(List.of(entityFrom(first), entityFrom(second)));
        when(securityEventRepository.count(any(Specification.class))).thenReturn(1L);
        when(securityEventRepository.countDistinctClientIps(any(Specification.class))).thenReturn(2L);
        List<AlertEvaluationResponse> results = alertService.evaluateAll();
        assertEquals(2, results.size());
    }

    @Test
    void doesNotFireBelowThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("unique_ips", 5, null));
        when(alertDefinitionRepository.findById(defined.getId())).thenReturn(Optional.of(entityFrom(defined)));
        when(securityEventRepository.countDistinctClientIps(any(Specification.class))).thenReturn(2L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertFalse(result.getFiring());
        assertEquals(2L, result.getValue());
    }

    @Test
    void unknownAlertIdFails() {
        when(alertDefinitionRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> alertService.evaluate("missing"));
    }

    private static DefineAlertRequest request(String metric, int threshold, String category) {
        DefineAlertRequest request = new DefineAlertRequest();
        request.setConfigId(14227L);
        request.setCategory(category);
        request.setMetric(metric);
        request.setWindow("5m");
        request.setThreshold(threshold);
        return request;
    }

    private static AlertDefinitionEntity entityFrom(AlertDefinitionResponse response) {
        AlertDefinitionEntity entity = new AlertDefinitionEntity();
        entity.setId(response.getId());
        entity.setConfigId(response.getConfigId());
        entity.setCategory(response.getCategory());
        entity.setMetric(response.getMetric());
        entity.setWindow(response.getWindow());
        entity.setThreshold(response.getThreshold());
        entity.setCreatedAt(Instant.parse("2026-05-20T15:00:00Z"));
        return entity;
    }
}
