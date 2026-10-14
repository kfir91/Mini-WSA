package com.miniwsa.analytics.service;

import com.miniwsa.analytics.dto.response.AttackerStatsDto;
import com.miniwsa.analytics.dto.response.CategoryStatsDto;
import com.miniwsa.analytics.dto.response.PathStatsDto;
import com.miniwsa.analytics.dto.response.SummaryResponse;
import com.miniwsa.analytics.dto.response.TimeRangeDto;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final SecurityEventRepository repository;

    public StatsService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    public SummaryResponse summary(Long configId, Instant from, Instant to) {
        long total = repository.countForSummary(configId, from, to);
        Map<String, CategoryStatsDto> byCategory = new LinkedHashMap<>();
        for (SecurityEventRepository.CategoryAggregate row : repository.aggregateByCategory(configId, from, to)) {
            byCategory.put(row.getCategory(), new CategoryStatsDto(row.getCount(), round(row.getAvgThreatScore())));
        }
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (SecurityEventRepository.ActionAggregate row : repository.aggregateByAction(configId, from, to)) {
            byAction.put(row.getAction(), row.getCount());
        }
        List<AttackerStatsDto> attackers = repository.topAttackers(configId, from, to).stream()
                .map(row -> new AttackerStatsDto(row.getClientIp(), row.getCount(), round(row.getAvgThreatScore())))
                .toList();
        List<PathStatsDto> paths = repository.topPaths(configId, from, to).stream()
                .map(row -> new PathStatsDto(row.getPath(), row.getCount()))
                .toList();
        return new SummaryResponse(configId, new TimeRangeDto(from, to), total, byCategory, byAction, attackers, paths);
    }

    private static double round(Double value) {
        if (value == null) {
            return 0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
