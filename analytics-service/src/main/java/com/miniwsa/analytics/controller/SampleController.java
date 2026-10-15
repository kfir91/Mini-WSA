package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.dto.response.SamplePageResponse;
import com.miniwsa.analytics.service.SampleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping("/v1/events/samples")
    public SamplePageResponse samples(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer minThreatScore,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return sampleService.samples(configId, from, to, category, action, minThreatScore, limit, offset);
    }
}
