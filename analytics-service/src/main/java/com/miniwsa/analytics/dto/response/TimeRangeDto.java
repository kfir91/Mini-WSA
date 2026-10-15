package com.miniwsa.analytics.dto.response;

import java.time.Instant;

public class TimeRangeDto {

    private final Instant from;
    private final Instant to;

    public TimeRangeDto(Instant from, Instant to) {
        this.from = from;
        this.to = to;
    }

    public Instant getFrom() {
        return from;
    }

    public Instant getTo() {
        return to;
    }
}
