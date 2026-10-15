package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
import com.miniwsa.analytics.api.DefineAlertRequest;
import com.miniwsa.analytics.mapper.AlertMapper;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private SecurityEventRepository repository;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T15:00:00Z"), ZoneOffset.UTC);
        alertService = new AlertService(repository, Mappers.getMapper(AlertMapper.class), clock);
    }

    @Test
    void firesWhenEventCountMeetsThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 3, null));
        when(repository.count(any(Specification.class))).thenReturn(3L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.getFiring());
        assertEquals(3L, result.getValue());
    }

    @Test
    void countsCategoryWhenDefined() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 2, "INJECTION"));
        when(repository.count(any(Specification.class))).thenReturn(4L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.getFiring());
        assertEquals(4L, result.getValue());
    }

    @Test
    void evaluateAllReturnsEveryRule() {
        alertService.define(request("event_count", 3, null));
        alertService.define(request("unique_ips", 5, null));
        when(repository.count(any(Specification.class))).thenReturn(1L);
        when(repository.countDistinctClientIps(any(Specification.class))).thenReturn(2L);
        List<AlertEvaluationResponse> results = alertService.evaluateAll();
        assertEquals(2, results.size());
    }

    @Test
    void doesNotFireBelowThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("unique_ips", 5, null));
        when(repository.countDistinctClientIps(any(Specification.class))).thenReturn(2L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertFalse(result.getFiring());
        assertEquals(2L, result.getValue());
    }

    @Test
    void unknownAlertIdFails() {
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
}
