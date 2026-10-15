package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.api.SamplePageResponse;
import com.miniwsa.analytics.api.SamplesApi;
import com.miniwsa.analytics.service.SampleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Samples HTTP adapter implementing the generated OpenAPI interface.
 */
@RestController
public class SampleController implements SamplesApi {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @Override
    public ResponseEntity<SamplePageResponse> getSamples(
            Long configId,
            Instant from,
            Instant to,
            String category,
            String action,
            Integer minThreatScore,
            Integer limit,
            Integer offset) {
        return ResponseEntity.ok(
                sampleService.samples(configId, from, to, category, action, minThreatScore, limit, offset));
    }
}
