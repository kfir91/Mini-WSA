package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.SummaryResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.repository.SummaryAggregationQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private SummaryAggregationQuery summaryQuery;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(summaryQuery);
    }

    @Test
    void buildsSummaryFromDaoAggregates() {
        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");
        when(summaryQuery.count(14227L, from, to)).thenReturn(3L);
        when(summaryQuery.byCategory(14227L, from, to)).thenReturn(List.of(category("INJECTION", 2L, 70.0)));
        when(summaryQuery.byAction(14227L, from, to)).thenReturn(List.of(action("DENY", 2L), action("ALERT", 1L)));
        when(summaryQuery.topAttackers(14227L, from, to)).thenReturn(List.of(attacker("203.0.113.42", 2L, 80.0)));
        when(summaryQuery.topPaths(14227L, from, to)).thenReturn(List.of(path("/login", 2L)));

        SummaryResponse response = statsService.summary(14227L, from, to);

        assertEquals(14227L, response.getConfigId());
        assertEquals(from, response.getTimeRange().getFrom());
        assertEquals(3L, response.getTotalEvents());
        assertEquals(2L, response.getByCategory().get("INJECTION").getCount());
        assertEquals(70.0, response.getByCategory().get("INJECTION").getAvgThreatScore());
        assertEquals(2L, response.getByAction().get("DENY"));
        assertEquals("203.0.113.42", response.getTopAttackers().get(0).getClientIp());
        assertEquals("/login", response.getTopTargetedPaths().get(0).getPath());
    }

    @Test
    void omittedConfigIdAggregatesAll() {
        when(summaryQuery.count(isNull(), any(), any())).thenReturn(10L);
        when(summaryQuery.byCategory(isNull(), any(), any())).thenReturn(List.of());
        when(summaryQuery.byAction(isNull(), any(), any())).thenReturn(List.of());
        when(summaryQuery.topAttackers(isNull(), any(), any())).thenReturn(List.of());
        when(summaryQuery.topPaths(isNull(), any(), any())).thenReturn(List.of());
        SummaryResponse response = statsService.summary(null, null, null);
        assertNull(response.getConfigId());
        assertEquals(10L, response.getTotalEvents());
        assertNull(response.getTimeRange().getFrom());
        assertNull(response.getTimeRange().getTo());
    }

    private static SecurityEventRepository.CategoryAggregate category(String name, long count, double avg) {
        return new SecurityEventRepository.CategoryAggregate() {
            @Override
            public String getCategory() {
                return name;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public Double getAvgThreatScore() {
                return avg;
            }
        };
    }

    private static SecurityEventRepository.ActionAggregate action(String name, long count) {
        return new SecurityEventRepository.ActionAggregate() {
            @Override
            public String getAction() {
                return name;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private static SecurityEventRepository.AttackerAggregate attacker(String ip, long count, double avg) {
        return new SecurityEventRepository.AttackerAggregate() {
            @Override
            public String getClientIp() {
                return ip;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public Double getAvgThreatScore() {
                return avg;
            }
        };
    }

    private static SecurityEventRepository.PathAggregate path(String value, long count) {
        return new SecurityEventRepository.PathAggregate() {
            @Override
            public String getPath() {
                return value;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
