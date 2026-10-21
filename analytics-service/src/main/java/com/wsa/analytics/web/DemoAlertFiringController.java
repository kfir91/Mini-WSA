package com.wsa.analytics.web;

import com.wsa.analytics.service.DemoAlertFiringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dashboard demo hook: POST to rewind existing event timestamps into alert windows.
 */
@RestController
@RequiredArgsConstructor
public class DemoAlertFiringController {

    private final DemoAlertFiringService demoAlertFiringService;

    @PostMapping(value = "/demo/alerts/fire", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> fireDemoAlerts() {
        return demoAlertFiringService.makeDemoFire();
    }
}
