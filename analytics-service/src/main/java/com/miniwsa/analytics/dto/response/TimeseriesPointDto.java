package com.miniwsa.analytics.dto.response;

import java.time.Instant;

public class TimeseriesPointDto {

    private final Instant bucket;
    private final long count;

    public TimeseriesPointDto(Instant bucket, long count) {
        this.bucket = bucket;
        this.count = count;
    }

    public Instant getBucket() {
        return bucket;
    }

    public long getCount() {
        return count;
    }
}
