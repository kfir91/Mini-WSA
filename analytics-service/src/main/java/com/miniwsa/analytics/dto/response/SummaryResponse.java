package com.miniwsa.analytics.dto.response;

import java.util.List;
import java.util.Map;

public class SummaryResponse {

    private final Long configId;
    private final TimeRangeDto timeRange;
    private final long totalEvents;
    private final Map<String, CategoryStatsDto> byCategory;
    private final Map<String, Long> byAction;
    private final List<AttackerStatsDto> topAttackers;
    private final List<PathStatsDto> topTargetedPaths;

    public SummaryResponse(
            Long configId,
            TimeRangeDto timeRange,
            long totalEvents,
            Map<String, CategoryStatsDto> byCategory,
            Map<String, Long> byAction,
            List<AttackerStatsDto> topAttackers,
            List<PathStatsDto> topTargetedPaths) {
        this.configId = configId;
        this.timeRange = timeRange;
        this.totalEvents = totalEvents;
        this.byCategory = byCategory;
        this.byAction = byAction;
        this.topAttackers = topAttackers;
        this.topTargetedPaths = topTargetedPaths;
    }

    public Long getConfigId() {
        return configId;
    }

    public TimeRangeDto getTimeRange() {
        return timeRange;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public Map<String, CategoryStatsDto> getByCategory() {
        return byCategory;
    }

    public Map<String, Long> getByAction() {
        return byAction;
    }

    public List<AttackerStatsDto> getTopAttackers() {
        return topAttackers;
    }

    public List<PathStatsDto> getTopTargetedPaths() {
        return topTargetedPaths;
    }
}
