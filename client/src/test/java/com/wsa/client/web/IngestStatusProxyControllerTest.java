package com.wsa.client.web;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestStatusProxyControllerTest {

    private static final MockWebServer INGEST = new MockWebServer();
    private static final MockWebServer ANALYTICS = new MockWebServer();

    static {
        try {
            INGEST.start();
            ANALYTICS.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        registry.add("wsa.client.ingest-base-url",
                () -> INGEST.url("/").toString().replaceAll("/$", ""));
        registry.add("wsa.client.analytics-base-url",
                () -> ANALYTICS.url("/").toString().replaceAll("/$", ""));
    }

    @AfterAll
    static void shutdown() throws IOException {
        INGEST.shutdown();
        ANALYTICS.shutdown();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void proxiesStatusFromIngest() throws Exception {
        INGEST.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"eventId\":\"evt-1\",\"status\":\"done\",\"attemptCount\":1,\"createdAt\":\"2026-01-01T00:00:00Z\"}"));

        mockMvc.perform(get("/hub/events/evt-1/status"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"eventId\":\"evt-1\",\"status\":\"done\",\"attemptCount\":1,\"createdAt\":\"2026-01-01T00:00:00Z\"}"));

        RecordedRequest recorded = INGEST.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("GET", recorded.getMethod());
        assertEquals("/v1/events/evt-1/status", recorded.getPath());
    }
}
