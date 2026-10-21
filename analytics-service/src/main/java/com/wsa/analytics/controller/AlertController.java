package com.wsa.analytics.controller;

import com.wsa.analytics.api.AlertDefinitionResponse;
import com.wsa.analytics.api.AlertEvaluationResponse;
import com.wsa.analytics.api.AlertsApi;
import com.wsa.analytics.api.DefineAlertRequest;
import com.wsa.analytics.service.AlertService;
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
