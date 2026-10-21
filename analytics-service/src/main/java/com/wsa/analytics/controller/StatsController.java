package com.wsa.analytics.controller;

import com.wsa.analytics.api.StatsApi;
import com.wsa.analytics.api.SummaryResponse;
import com.wsa.analytics.api.TimeseriesResponse;
import com.wsa.analytics.service.StatsService;
import com.wsa.analytics.service.TimeseriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Stats HTTP adapter implementing the generated OpenAPI interface.
 */
@RestController
@RequiredArgsConstructor
public class StatsController implements StatsApi {

    private final StatsService statsService;
    private final TimeseriesService timeseriesService;

    @Override
    public ResponseEntity<SummaryResponse> getSummary(Long configId, Instant from, Instant to) {
        return ResponseEntity.ok(statsService.summary(configId, from, to));
    }

    @Override
    public ResponseEntity<TimeseriesResponse> getTimeseries(String interval, Long configId, Instant from, Instant to) {
        return ResponseEntity.ok(timeseriesService.timeseries(interval, configId, from, to));
    }
}
