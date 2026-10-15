package com.miniwsa.analytics.dto.response;

public class AttackerStatsDto {

    private final String clientIp;
    private final long count;
    private final double avgThreatScore;

    public AttackerStatsDto(String clientIp, long count, double avgThreatScore) {
        this.clientIp = clientIp;
        this.count = count;
        this.avgThreatScore = avgThreatScore;
    }

    public String getClientIp() {
        return clientIp;
    }

    public long getCount() {
        return count;
    }

    public double getAvgThreatScore() {
        return avgThreatScore;
    }
}
