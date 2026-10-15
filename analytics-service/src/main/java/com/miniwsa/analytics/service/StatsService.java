package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.AttackerStats;
import com.miniwsa.analytics.api.CategoryStats;
import com.miniwsa.analytics.api.PathStats;
import com.miniwsa.analytics.api.SummaryResponse;
import com.miniwsa.analytics.api.TimeRange;
import com.miniwsa.analytics.repository.SummaryAggregationQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds aggregated attack summaries from persisted enriched events.
 */
@Service
public class StatsService {

    private final SummaryAggregationQuery summaryQuery;

    public StatsService(SummaryAggregationQuery summaryQuery) {
        this.summaryQuery = summaryQuery;
    }

    public SummaryResponse summary(Long configId, Instant from, Instant to) {
        long total = summaryQuery.count(configId, from, to);
        Map<String, CategoryStats> byCategory = new LinkedHashMap<>();
        for (var row : summaryQuery.byCategory(configId, from, to)) {
            byCategory.put(row.getCategory(), new CategoryStats(row.getCount(), round(row.getAvgThreatScore())));
        }
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (var row : summaryQuery.byAction(configId, from, to)) {
            byAction.put(row.getAction(), row.getCount());
        }
        List<AttackerStats> attackers = summaryQuery.topAttackers(configId, from, to).stream()
                .map(row -> new AttackerStats(row.getClientIp(), row.getCount(), round(row.getAvgThreatScore())))
                .toList();
        List<PathStats> paths = summaryQuery.topPaths(configId, from, to).stream()
                .map(row -> new PathStats(row.getPath(), row.getCount()))
                .toList();
        SummaryResponse response = new SummaryResponse(total, byCategory, byAction, attackers, paths);
        response.setConfigId(configId);
        response.setTimeRange(new TimeRange(from, to));
        return response;
    }

    private static double round(Double value) {
        if (value == null) {
            return 0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
