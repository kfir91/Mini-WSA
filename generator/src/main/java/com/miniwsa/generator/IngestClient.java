package com.miniwsa.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.miniwsa.contracts.SecurityEventMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class IngestClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String ingestUrl;

    public IngestClient(String baseUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.ingestUrl = baseUrl.replaceAll("/$", "") + "/v1/events/ingest";
    }

    public int post(SecurityEventMessage event) throws Exception {
        String body = objectMapper.writeValueAsString(event);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ingestUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }
}
