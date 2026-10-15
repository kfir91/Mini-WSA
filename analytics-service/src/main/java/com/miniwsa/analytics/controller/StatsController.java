package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.api.StatsApi;
import com.miniwsa.analytics.api.SummaryResponse;
import com.miniwsa.analytics.api.TimeseriesResponse;
import com.miniwsa.analytics.service.StatsService;
import com.miniwsa.analytics.service.TimeseriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Stats HTTP adapter implementing the generated OpenAPI interface.
 */
@RestController
public class StatsController implements StatsApi {

    private final StatsService statsService;
    private final TimeseriesService timeseriesService;

    public StatsController(StatsService statsService, TimeseriesService timeseriesService) {
        this.statsService = statsService;
        this.timeseriesService = timeseriesService;
    }

    @Override
    public ResponseEntity<SummaryResponse> getSummary(Long configId, Instant from, Instant to) {
        return ResponseEntity.ok(statsService.summary(configId, from, to));
    }

    @Override
    public ResponseEntity<TimeseriesResponse> getTimeseries(String interval, Long configId, Instant from, Instant to) {
        return ResponseEntity.ok(timeseriesService.timeseries(interval, configId, from, to));
    }
}
