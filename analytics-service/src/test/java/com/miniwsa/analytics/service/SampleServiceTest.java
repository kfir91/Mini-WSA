package com.miniwsa.analytics.service;

import com.miniwsa.analytics.dto.response.SamplePageResponse;
import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

    @Mock
    private SecurityEventRepository repository;

    private SampleService sampleService;

    @BeforeEach
    void setUp() {
        sampleService = new SampleService(repository);
    }

    @Test
    void defaultsPageAndSize() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        SamplePageResponse response = sampleService.samples(null, null, null, null, null, null, null, null);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
    }

    @Test
    void capsPageSizeAt100() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 100), 0));
        sampleService.samples(null, null, null, null, null, null, 0, 500);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void mapsStoredEventToSample() {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setEventId("evt-1");
        entity.setEventTimestamp(Instant.parse("2026-05-20T14:32:10Z"));
        entity.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        entity.setConfigId(1L);
        entity.setPolicyId("pol");
        entity.setClientIp("1.1.1.1");
        entity.setHostname("h");
        entity.setPath("/login");
        entity.setMethod("POST");
        entity.setStatusCode(403);
        entity.setUserAgent("ua");
        entity.setRuleId("r");
        entity.setRuleName("n");
        entity.setRuleMessage("m");
        entity.setSeverity("CRITICAL");
        entity.setCategory("INJECTION");
        entity.setAction("DENY");
        entity.setCountry("CN");
        entity.setCity("Beijing");
        entity.setRequestSize(1);
        entity.setResponseSize(1);
        entity.setAttackType("SQL/Command Injection");
        entity.setThreatScore(75);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        SamplePageResponse response = sampleService.samples(1L, null, null, "INJECTION", "DENY", 50, 0, 20);
        assertEquals(1, response.getItems().size());
        assertEquals("evt-1", response.getItems().get(0).getEventId());
        assertEquals(75, response.getItems().get(0).getThreatScore());
        assertEquals("SQL/Command Injection", response.getItems().get(0).getAttackType());
    }
}
