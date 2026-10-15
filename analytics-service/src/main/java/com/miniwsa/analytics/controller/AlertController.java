package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
import com.miniwsa.analytics.api.AlertsApi;
import com.miniwsa.analytics.api.DefineAlertRequest;
import com.miniwsa.analytics.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Alerts HTTP adapter implementing the generated OpenAPI interface.
 */
@RestController
@RequiredArgsConstructor
public class AlertController implements AlertsApi {

    private final AlertService alertService;

    @Override
    public ResponseEntity<AlertDefinitionResponse> defineAlert(DefineAlertRequest defineAlertRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.define(defineAlertRequest));
    }

    @Override
    public ResponseEntity<List<AlertEvaluationResponse>> evaluateAlerts(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return ResponseEntity.ok(alertService.evaluateAll());
        }
        return ResponseEntity.ok(List.of(alertService.evaluate(alertId)));
    }
}
