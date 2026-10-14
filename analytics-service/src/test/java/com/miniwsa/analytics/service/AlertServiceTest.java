package com.miniwsa.analytics.service;

import com.miniwsa.analytics.dto.request.DefineAlertRequest;
import com.miniwsa.analytics.dto.response.AlertDefinitionResponse;
import com.miniwsa.analytics.dto.response.AlertEvaluationResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private SecurityEventRepository repository;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T15:00:00Z"), ZoneOffset.UTC);
        alertService = new AlertService(repository, clock);
    }

    @Test
    void firesWhenEventCountMeetsThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 3, null));
        when(repository.countSince(eq(14227L), isNull(), any())).thenReturn(3L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.isFiring());
        assertEquals(3L, result.getValue());
    }

    @Test
    void countsCategoryWhenDefined() {
        AlertDefinitionResponse defined = alertService.define(request("event_count", 2, "INJECTION"));
        when(repository.countSince(eq(14227L), eq("INJECTION"), any())).thenReturn(4L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertTrue(result.isFiring());
        assertEquals(4L, result.getValue());
    }

    @Test
    void evaluateAllReturnsEveryRule() {
        alertService.define(request("event_count", 3, null));
        alertService.define(request("unique_ips", 5, null));
        when(repository.countSince(eq(14227L), isNull(), any())).thenReturn(1L);
        when(repository.countDistinctIpsSince(eq(14227L), any())).thenReturn(2L);
        List<AlertEvaluationResponse> results = alertService.evaluateAll();
        assertEquals(2, results.size());
    }

    @Test
    void doesNotFireBelowThreshold() {
        AlertDefinitionResponse defined = alertService.define(request("unique_ips", 5, null));
        when(repository.countDistinctIpsSince(eq(14227L), any())).thenReturn(2L);
        AlertEvaluationResponse result = alertService.evaluate(defined.getId());
        assertFalse(result.isFiring());
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
