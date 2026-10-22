package com.wsa.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Thin HTTP client for ingest + analytics bases discovered at runtime.
 * Paths match OpenAPI; no business logic.
 */
final class WsaApiClient {

    private static final String PATH_HEALTH = "/actuator/health";
    private static final String PATH_INGEST = "/v1/events/ingest";
    private static final String PATH_SUMMARY = "/v1/stats/summary";
    private static final String PATH_SAMPLES = "/v1/events/samples";
    private static final String FIXTURE_EVENT = "fixtures/security-event.json";
    private static final int SAMPLES_PAGE_SIZE = 100;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INGEST_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final String ingestBase;
    private final String analyticsBase;
    private final long configId;

    WsaApiClient(String ingestBase, String analyticsBase, long configId) {
        this.ingestBase = ingestBase;
        this.analyticsBase = analyticsBase;
        this.configId = configId;
    }

    HttpResponse<String> postIngest(String eventId) throws Exception {
        ObjectNode event = loadFixtureEvent(eventId);
        return postJson(ingestBase + PATH_INGEST, MAPPER.writeValueAsString(event));
    }

    HttpResponse<String> postIngestBatch(Collection<String> eventIds) throws Exception {
        ArrayNode batch = MAPPER.createArrayNode();
        for (String eventId : eventIds) {
            batch.add(loadFixtureEvent(eventId));
        }
        return postJson(ingestBase + PATH_INGEST, MAPPER.writeValueAsString(batch));
    }

    long totalEvents() throws Exception {
        HttpResponse<String> response = get(analyticsBase + PATH_SUMMARY + "?configId=" + configId);
        assertEquals(HttpStatus.OK.value(), response.statusCode(), () -> "summary body=" + response.body());
        return MAPPER.readTree(response.body()).path("totalEvents").asLong();
    }

    List<String> matchingSampleEventIds(String eventId) throws Exception {
        List<String> matches = new ArrayList<>();
        for (JsonNode item : sampleItems()) {
            if (eventId.equals(item.path("eventId").asText())) {
                matches.add(eventId);
            }
        }
        return matches;
    }

    Set<String> sampleEventIdsPresent(Collection<String> wanted) throws Exception {
        Set<String> wantedSet = new LinkedHashSet<>(wanted);
        Set<String> found = new LinkedHashSet<>();
        for (JsonNode item : sampleItems()) {
            String id = item.path("eventId").asText();
            if (wantedSet.contains(id)) {
                found.add(id);
            }
        }
        return found;
    }

    List<JsonNode> sampleItems() throws Exception {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            String url = analyticsBase + PATH_SAMPLES
                    + "?configId=" + configId
                    + "&limit=" + SAMPLES_PAGE_SIZE
                    + "&offset=" + offset;
            HttpResponse<String> response = get(url);
            assertEquals(HttpStatus.OK.value(), response.statusCode(), () -> "samples body=" + response.body());
            JsonNode items = MAPPER.readTree(response.body()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode item : items) {
                all.add(item);
            }
            if (items.size() < SAMPLES_PAGE_SIZE) {
                break;
            }
            offset += SAMPLES_PAGE_SIZE;
            if (offset > 10_000) {
                break;
            }
        }
        return all;
    }

    boolean isHealthyIngest() {
        return isHealthy(ingestBase);
    }

    boolean isHealthyAnalytics() {
        return isHealthy(analyticsBase);
    }

    private ObjectNode loadFixtureEvent(String eventId) throws Exception {
        ObjectNode event = (ObjectNode) MAPPER.readTree(classpathBytes(FIXTURE_EVENT));
        event.put("eventId", eventId);
        event.put("timestamp", Instant.now().toString());
        event.put("configId", configId);
        return event;
    }

    private boolean isHealthy(String base) {
        try {
            return get(base + PATH_HEALTH).statusCode() == HttpStatus.OK.value();
        } catch (Exception ex) {
            return false;
        }
    }

    private static HttpResponse<String> get(String absoluteUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(absoluteUrl))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String absoluteUrl, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(absoluteUrl))
                .timeout(INGEST_TIMEOUT)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] classpathBytes(String path) throws Exception {
        try (InputStream in = WsaApiClient.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return in.readAllBytes();
        }
    }
}
