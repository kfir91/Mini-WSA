package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.dto.response.TimeseriesResponse;
import com.miniwsa.analytics.service.TimeseriesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class TimeseriesController {

    private final TimeseriesService timeseriesService;

    public TimeseriesController(TimeseriesService timeseriesService) {
        this.timeseriesService = timeseriesService;
    }

    @GetMapping("/v1/stats/timeseries")
    public TimeseriesResponse timeseries(
            @RequestParam String interval,
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return timeseriesService.timeseries(interval, configId, from, to);
    }
}
