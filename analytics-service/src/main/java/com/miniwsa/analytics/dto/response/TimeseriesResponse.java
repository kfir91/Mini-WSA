package com.miniwsa.analytics.dto.response;

import java.util.List;

public class TimeseriesResponse {

    private final String interval;
    private final List<TimeseriesPointDto> points;

    public TimeseriesResponse(String interval, List<TimeseriesPointDto> points) {
        this.interval = interval;
        this.points = points;
    }

    public String getInterval() {
        return interval;
    }

    public List<TimeseriesPointDto> getPoints() {
        return points;
    }
}
