package com.miniwsa.analytics.service;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.manager.EnrichmentManager;
import com.miniwsa.analytics.mapper.SecurityEventMapper;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.GeoLocationMessage;
import com.miniwsa.contracts.RuleMessage;
import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.contracts.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventEnrichmentServiceTest {

    @Mock
    private SecurityEventRepository repository;

    private EventEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new EventEnrichmentService(
                repository,
                new EnrichmentManager(),
                Mappers.getMapper(SecurityEventMapper.class),
                Duration.ofMinutes(10));
    }

    @Test
    void persistsClassifiedAndScoredEvent() {
        when(repository.existsById("evt-1")).thenReturn(false);
        when(repository.countByClientIpAndEventTimestampGreaterThanEqual(eq("203.0.113.42"), any())).thenReturn(0L);
        service.enrichAndStore(message("evt-1", "/home"));
        ArgumentCaptor<SecurityEventEntity> captor = ArgumentCaptor.forClass(SecurityEventEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("SQL/Command Injection", captor.getValue().getAttackType());
        assertEquals(60, captor.getValue().getThreatScore());
    }

    @Test
    void skipsDuplicateEventId() {
        when(repository.existsById("evt-1")).thenReturn(true);
        service.enrichAndStore(message("evt-1", "/home"));
        verify(repository, never()).save(any());
    }

    @Test
    void sixthEventFromSameIpGetsRepeatOffenderBonus() {
        when(repository.existsById("evt-6")).thenReturn(false);
        when(repository.countByClientIpAndEventTimestampGreaterThanEqual(eq("203.0.113.42"), any())).thenReturn(6L);
        service.enrichAndStore(message("evt-6", "/home"));
        ArgumentCaptor<SecurityEventEntity> captor = ArgumentCaptor.forClass(SecurityEventEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(75, captor.getValue().getThreatScore());
    }

    @Test
    void duplicateInsertRaceIsIgnored() {
        when(repository.existsById("evt-race")).thenReturn(false);
        when(repository.countByClientIpAndEventTimestampGreaterThanEqual(eq("203.0.113.42"), any())).thenReturn(0L);
        doThrow(new DataIntegrityViolationException("dup")).when(repository).save(any());
        service.enrichAndStore(message("evt-race", "/home"));
        verify(repository).save(any());
    }

    @Test
    void stampsReceivedAtWhenMissing() {
        when(repository.existsById("evt-2")).thenReturn(false);
        when(repository.countByClientIpAndEventTimestampGreaterThanEqual(eq("203.0.113.42"), any())).thenReturn(0L);
        SecurityEventMessage event = message("evt-2", "/home");
        event.setReceivedAt(null);
        service.enrichAndStore(event);
        ArgumentCaptor<SecurityEventEntity> captor = ArgumentCaptor.forClass(SecurityEventEntity.class);
        verify(repository).save(captor.capture());
        assertNotNull(captor.getValue().getReceivedAt());
    }

    private static SecurityEventMessage message(String id, String path) {
        RuleMessage rule = new RuleMessage();
        rule.setId("950001");
        rule.setName("SQL_INJECTION");
        rule.setMessage("detected");
        rule.setSeverity(Severity.CRITICAL);
        rule.setCategory(AttackCategory.INJECTION);
        GeoLocationMessage geo = new GeoLocationMessage();
        geo.setCountry("CN");
        geo.setCity("Beijing");
        SecurityEventMessage event = new SecurityEventMessage();
        event.setEventId(id);
        event.setTimestamp(Instant.parse("2026-05-20T14:32:10Z"));
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        event.setConfigId(14227L);
        event.setPolicyId("pol_web1");
        event.setClientIp("203.0.113.42");
        event.setHostname("www.example.com");
        event.setPath(path);
        event.setMethod("POST");
        event.setStatusCode(403);
        event.setUserAgent("Mozilla/5.0");
        event.setRule(rule);
        event.setAction(Action.DENY);
        event.setGeoLocation(geo);
        event.setRequestSize(1024);
        event.setResponseSize(256);
        return event;
    }
}
