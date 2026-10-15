package com.miniwsa.analytics.service;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.support.SecurityEventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAlertFiringServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private SecurityEventRepository securityEventRepository;

    private DemoAlertFiringService service;

    @BeforeEach
    void setUp() {
        service = new DemoAlertFiringService(securityEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void makeDemoFireStampsExistingRowsIntoWindow() {
        when(securityEventRepository.findAll(any(Specification.class))).thenReturn(List.of());
        AtomicInteger seq = new AtomicInteger();
        when(securityEventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    SecurityEventEntity event = SecurityEventFixtures.event(
                            "evt-" + seq.incrementAndGet(),
                            "INJECTION",
                            "DENY",
                            80,
                            NOW.minusSeconds(86_400));
                    return new PageImpl<>(List.of(event), pageable, 1);
                });
        when(securityEventRepository.count(any(Specification.class))).thenReturn(7L);
        when(securityEventRepository.countDistinctClientIps(any(Specification.class))).thenReturn(3L);

        Map<String, Object> result = service.makeDemoFire();

        assertTrue(((Number) result.get("refreshed")).intValue() >= 7);
        assertEquals(7L, result.get("eventsInLast5m"));
        assertEquals(3L, result.get("uniqueIpsInLast5m"));
        verify(securityEventRepository, atLeastOnce()).saveAll(any());
    }
}
