package com.wsa.analytics.service;

import com.wsa.analytics.api.TimeseriesPoint;
import com.wsa.analytics.api.TimeseriesResponse;
import com.wsa.analytics.repository.TimeseriesBucketQuery;
import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorMessage;
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
            throw ApiException.validation(ErrorMessage.INVALID_INTERVAL);
        }
        var points = timeseriesBucketQuery.query(pgInterval, configId, from, to).stream()
                .map(row -> TimeseriesPoint.builder().bucket(row.getBucket()).count(row.getCount()).build())
                .toList();
        return TimeseriesResponse.builder().interval(interval).points(points).build();
    }
}
