package com.wsa.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Actuator health / metrics reads for pipeline ITs. */
final class ActuatorProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String ingestBase;
    private final String analyticsBase;
    private final String clientBase;

    ActuatorProbe(String ingestBase, String analyticsBase, String clientBase) {
        this.ingestBase = ingestBase;
        this.analyticsBase = analyticsBase;
        this.clientBase = clientBase;
    }

    boolean ingestHealthy() {
        return healthy(ingestBase);
    }

    boolean analyticsHealthy() {
        return healthy(analyticsBase);
    }

    boolean clientHealthy() {
        return clientBase != null && healthy(clientBase);
    }

    double outboxPendingGauge() throws Exception {
        HttpResponse<String> response = get(ingestBase + "/actuator/metrics/ingest_outbox_pending");
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new IllegalStateException("metrics status=" + response.statusCode() + " body=" + response.body());
        }
        JsonNode measurements = MAPPER.readTree(response.body()).path("measurements");
        if (!measurements.isArray() || measurements.isEmpty()) {
            return Double.NaN;
        }
        return measurements.get(0).path("value").asDouble();
    }

    private static boolean healthy(String base) {
        try {
            return get(base + "/actuator/health").statusCode() == HttpStatus.OK.value();
        } catch (Exception ex) {
            return false;
        }
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
