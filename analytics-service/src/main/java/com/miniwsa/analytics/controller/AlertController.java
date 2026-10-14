package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.alert.AlertDefinition;
import com.miniwsa.analytics.dto.request.DefineAlertRequest;
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

@RestController
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/v1/alerts/define")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDefinition define(@Valid @RequestBody DefineAlertRequest request) {
        return alertService.define(request);
    }

    @GetMapping("/v1/alerts/evaluate")
    public AlertEvaluationResponse evaluate(@RequestParam String alertId) {
        return alertService.evaluate(alertId);
    }
}
