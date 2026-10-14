package com.miniwsa.analytics.dto.response;

public class PathStatsDto {

    private final String path;
    private final long count;

    public PathStatsDto(String path, long count) {
        this.path = path;
        this.count = count;
    }

    public String getPath() {
        return path;
    }

    public long getCount() {
        return count;
    }
}
