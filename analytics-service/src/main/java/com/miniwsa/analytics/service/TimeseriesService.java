package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.TimeseriesPoint;
import com.miniwsa.analytics.api.TimeseriesResponse;
import com.miniwsa.analytics.repository.TimeseriesBucketQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Buckets event counts into fixed time intervals.
 */
@Service
@RequiredArgsConstructor
public class TimeseriesService {

    private static final Map<String, String> INTERVALS = Map.of(
            "1m", "1 minute",
            "5m", "5 minutes",
            "1h", "1 hour");

    private final TimeseriesBucketQuery timeseriesBucketQuery;

    public TimeseriesResponse timeseries(String interval, Long configId, Instant from, Instant to) {
        String pgInterval = INTERVALS.get(interval);
        if (pgInterval == null) {
            throw new IllegalArgumentException("interval must be one of 1m, 5m, 1h");
        }
        var points = timeseriesBucketQuery.query(pgInterval, configId, from, to).stream()
                .map(row -> new TimeseriesPoint(row.getBucket(), row.getCount()))
                .toList();
        return new TimeseriesResponse(interval, points);
    }
}
