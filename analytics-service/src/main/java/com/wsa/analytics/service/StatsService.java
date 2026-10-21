package com.wsa.analytics.service;

import com.wsa.analytics.api.AttackerStats;
import com.wsa.analytics.api.CategoryStats;
import com.wsa.analytics.api.PathStats;
import com.wsa.analytics.api.SummaryResponse;
import com.wsa.analytics.api.TimeRange;
import com.wsa.analytics.repository.SummaryAggregationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds aggregated attack summaries from persisted enriched events.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final SummaryAggregationQuery summaryQuery;

    public SummaryResponse summary(Long configId, Instant from, Instant to) {
        long total = summaryQuery.count(configId, from, to);
        Map<String, CategoryStats> byCategory = new LinkedHashMap<>();
        for (var row : summaryQuery.byCategory(configId, from, to)) {
            byCategory.put(
                    row.getCategory(),
                    CategoryStats.builder()
                            .count(row.getCount())
                            .avgThreatScore(round(row.getAvgThreatScore()))
                            .build());
        }
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (var row : summaryQuery.byAction(configId, from, to)) {
            byAction.put(row.getAction(), row.getCount());
        }
        List<AttackerStats> attackers = summaryQuery.topAttackers(configId, from, to).stream()
                .map(row -> AttackerStats.builder()
                        .clientIp(row.getClientIp())
                        .count(row.getCount())
                        .avgThreatScore(round(row.getAvgThreatScore()))
                        .build())
                .toList();
        List<PathStats> paths = summaryQuery.topPaths(configId, from, to).stream()
                .map(row -> PathStats.builder().path(row.getPath()).count(row.getCount()).build())
                .toList();
        return SummaryResponse.builder()
                .totalEvents(total)
                .byCategory(byCategory)
                .byAction(byAction)
                .topAttackers(attackers)
                .topTargetedPaths(paths)
                .configId(configId)
                .timeRange(TimeRange.builder().from(from).to(to).build())
                .build();
    }

    private static double round(Double value) {
        if (value == null) {
            return 0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
