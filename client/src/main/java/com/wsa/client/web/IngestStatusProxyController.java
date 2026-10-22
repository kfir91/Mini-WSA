package com.wsa.client.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Set;

/**
 * Same-origin bridge for ingest outbox status (not under analytics {@code /v1/**} proxy).
 */
@RestController
public class IngestStatusProxyController {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final RestClient ingestRestClient;

    public IngestStatusProxyController(@Qualifier("ingestRestClient") RestClient ingestRestClient) {
        this.ingestRestClient = ingestRestClient;
    }

    @GetMapping("/hub/events/{eventId}/status")
    public ResponseEntity<byte[]> status(@PathVariable String eventId) {
        return ingestRestClient.get()
                .uri("/v1/events/{eventId}/status", eventId)
                .exchange((req, res) -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    res.getHeaders().forEach((name, values) -> {
                        if (name != null && !HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                            responseHeaders.put(name, values);
                        }
                    });
                    byte[] body = res.getBody() != null
                            ? StreamUtils.copyToByteArray(res.getBody())
                            : new byte[0];
                    return ResponseEntity.status(res.getStatusCode())
                            .headers(responseHeaders)
                            .body(body);
                });
    }
}
