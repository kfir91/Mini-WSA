package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.dto.request.DefineAlertRequest;
import com.miniwsa.analytics.dto.response.AlertDefinitionResponse;
import com.miniwsa.analytics.dto.response.AlertEvaluationResponse;
import com.miniwsa.analytics.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/v1/alerts/define")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDefinitionResponse define(@Valid @RequestBody DefineAlertRequest request) {
        return alertService.define(request);
    }

    @GetMapping("/v1/alerts/evaluate")
    public List<AlertEvaluationResponse> evaluate(@RequestParam(required = false) String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return alertService.evaluateAll();
        }
        return List.of(alertService.evaluate(alertId));
    }
}
