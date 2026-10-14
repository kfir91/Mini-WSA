package com.miniwsa.analytics.dto.response;

public class CategoryStatsDto {

    private final long count;
    private final double avgThreatScore;

    public CategoryStatsDto(long count, double avgThreatScore) {
        this.count = count;
        this.avgThreatScore = avgThreatScore;
    }

    public long getCount() {
        return count;
    }

    public double getAvgThreatScore() {
        return avgThreatScore;
    }
}
