package com.wsa.analytics.repository;

import com.wsa.analytics.entity.SecurityEventEntity;
import com.wsa.analytics.support.SecurityEventFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryTimeseriesBucketQueryTest {

    @Mock
    private SecurityEventRepository repository;

    @InjectMocks
    private InMemoryTimeseriesBucketQuery query;

    @Test
    void filtersByConfigIdAndTimeRange() {
        Instant t1 = Instant.parse("2026-05-20T14:00:00Z");
        Instant t2 = Instant.parse("2026-05-20T14:10:00Z");
        Instant t3 = Instant.parse("2026-05-20T15:00:00Z");
        SecurityEventEntity match = SecurityEventFixtures.event("a", "INJECTION", "DENY", 10, t2);
        SecurityEventEntity otherConfig = SecurityEventFixtures.event("b", "XSS", "DENY", 10, t2);
        otherConfig.setConfigId(999L);
        SecurityEventEntity tooEarly = SecurityEventFixtures.event("c", "XSS", "DENY", 10, t1);
        SecurityEventEntity tooLate = SecurityEventFixtures.event("d", "XSS", "DENY", 10, t3);
        when(repository.findAll()).thenReturn(List.of(match, otherConfig, tooEarly, tooLate));

        List<SecurityEventRepository.TimeBucketAggregate> buckets = query.query(
                "5 minutes",
                14227L,
                Instant.parse("2026-05-20T14:05:00Z"),
                Instant.parse("2026-05-20T14:30:00Z"));

        assertEquals(1, buckets.size());
        assertEquals(1L, buckets.get(0).getCount());
    }

    @Test
    void unsupportedIntervalThrows() {
        assertThrows(IllegalArgumentException.class, () -> query.query("2 minutes", null, null, null));
    }

    @Test
    void oneMinuteAndOneHourSteps() {
        Instant ts = Instant.parse("2026-05-20T14:32:10Z");
        when(repository.findAll()).thenReturn(List.of(
                SecurityEventFixtures.event("e1", "BOT", "ALERT", 5, ts)));

        assertEquals(1, query.query("1 minute", null, null, null).size());
        assertEquals(1, query.query("1 hour", null, null, null).size());
        assertTrue(query.query("1 hour", null, null, null).get(0).getCount() >= 1);
    }
}
