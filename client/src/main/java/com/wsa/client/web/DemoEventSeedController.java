package com.wsa.client.web;

import com.wsa.client.seed.DemoEventSeedRequest;
import com.wsa.client.seed.DemoEventSeedResponse;
import com.wsa.client.seed.DemoEventSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hub demo hook: seed N synthetic events through ingest-api (outbox → Kafka → analytics).
 * Path is under {@code /hub/**} so it is not caught by the analytics {@code /v1/**} / {@code /demo/**} proxy.
 */
@RestController
@RequiredArgsConstructor
public class DemoEventSeedController {

    private final DemoEventSeedService demoEventSeedService;

    @PostMapping(value = "/hub/seed/events", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DemoEventSeedResponse seedEvents(@RequestBody DemoEventSeedRequest request) {
        return demoEventSeedService.seed(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }
}
