package com.wsa.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** HTTP client against the client BFF (hub seed, analytics proxy, hub status). */
final class WsaClientFacade {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final int SAMPLES_PAGE_SIZE = 100;

    private final String clientBase;
    private final long configId;

    WsaClientFacade(String clientBase, long configId) {
        this.clientBase = clientBase;
        this.configId = configId;
    }

    HttpResponse<String> seedEvents(int count) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("count", count);
        body.put("configId", configId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(clientBase + "/hub/seed/events"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    long totalEvents() throws Exception {
        HttpResponse<String> response = get(clientBase + "/v1/stats/summary?configId=" + configId);
        assertEquals(HttpStatus.OK.value(), response.statusCode(), () -> "proxy summary body=" + response.body());
        return MAPPER.readTree(response.body()).path("totalEvents").asLong();
    }

    List<JsonNode> sampleItems() throws Exception {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            String url = clientBase + "/v1/events/samples"
                    + "?configId=" + configId
                    + "&limit=" + SAMPLES_PAGE_SIZE
                    + "&offset=" + offset;
            HttpResponse<String> response = get(url);
            assertEquals(HttpStatus.OK.value(), response.statusCode(), () -> "proxy samples body=" + response.body());
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

    Optional<String> anySampleEventId() throws Exception {
        for (JsonNode item : sampleItems()) {
            String id = item.path("eventId").asText(null);
            if (id != null && !id.isBlank()) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    String hubStatus(String eventId) throws Exception {
        HttpResponse<String> response = get(clientBase + "/hub/events/" + eventId + "/status");
        assertEquals(HttpStatus.OK.value(), response.statusCode(), () -> "hub status body=" + response.body());
        return MAPPER.readTree(response.body()).path("status").asText();
    }

    boolean isHealthy() {
        try {
            return get(clientBase + "/actuator/health").statusCode() == HttpStatus.OK.value();
        } catch (Exception ex) {
            return false;
        }
    }

    private static HttpResponse<String> get(String absoluteUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(absoluteUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
