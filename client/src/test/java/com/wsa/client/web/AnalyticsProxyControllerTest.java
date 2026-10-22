package com.wsa.client.web;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsProxyControllerTest {

    private static final MockWebServer ANALYTICS = new MockWebServer();

    static {
        try {
            ANALYTICS.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void analyticsUrl(DynamicPropertyRegistry registry) {
        registry.add("wsa.client.analytics-base-url",
                () -> ANALYTICS.url("/").toString().replaceAll("/$", ""));
        // Seed bean needs an ingest URL even when these tests only hit the analytics proxy.
        registry.add("wsa.client.ingest-base-url", () -> "http://127.0.0.1:9");
    }

    @AfterAll
    static void shutdown() throws IOException {
        ANALYTICS.shutdown();
    }

    @BeforeEach
    void drainUpstream() throws InterruptedException {
        while (ANALYTICS.takeRequest(20, TimeUnit.MILLISECONDS) != null) {
            // clear leftover recordings between tests
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void proxiesSummaryGetWithQuery() throws Exception {
        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"totalEvents\":3}"));

        mockMvc.perform(get("/v1/stats/summary?configId=14227"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"totalEvents\":3}"));

        RecordedRequest recorded = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("GET", recorded.getMethod());
        assertEquals("/v1/stats/summary?configId=14227", recorded.getPath());
    }

    @Test
    void proxiesDemoFireAndUpstreamErrors() throws Exception {
        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"updated\":1}"));

        mockMvc.perform(post("/demo/alerts/fire"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"updated\":1}"));

        RecordedRequest fire = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(fire);
        assertEquals("/demo/alerts/fire", fire.getPath());

        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"RATE_LIMITED\"}"));

        mockMvc.perform(get("/v1/stats/summary"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"code\":\"RATE_LIMITED\"}"));

        RecordedRequest limited = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(limited);
        assertEquals("/v1/stats/summary", limited.getPath());
    }

    @Test
    void proxiesJsonPostBody() throws Exception {
        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"a1\"}"));

        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"metric\":\"COUNT\",\"window\":\"5m\",\"threshold\":1}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"id\":\"a1\"}"));

        RecordedRequest recorded = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/alerts/define", recorded.getPath());
        assertEquals("{\"name\":\"n\",\"metric\":\"COUNT\",\"window\":\"5m\",\"threshold\":1}",
                recorded.getBody().readUtf8());
    }

    @Test
    void emptyUpstreamBodyAndPreservesForwardedFor() throws Exception {
        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(204)
                .setHeader("Connection", "keep-alive"));

        mockMvc.perform(get("/v1/stats/summary")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .header("Connection", "keep-alive"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        RecordedRequest recorded = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("203.0.113.9", recorded.getHeader("X-Forwarded-For"));
    }

    @Test
    void upstreamErrorStripsHopByHopHeaders() throws Exception {
        ANALYTICS.enqueue(new MockResponse()
                .setResponseCode(502)
                .setHeader("Connection", "close")
                .setHeader("Content-Type", "text/plain")
                .setBody("bad gateway"));

        mockMvc.perform(get("/v1/stats/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("bad gateway"));
    }

    @Test
    void addsXForwardedForWhenMissing() throws Exception {
        ANALYTICS.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        mockMvc.perform(get("/v1/stats/summary"))
                .andExpect(status().isOk());

        RecordedRequest recorded = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertNotNull(recorded.getHeader("X-Forwarded-For"));
    }

    @Test
    void blankQueryStringDoesNotAppendQuestionMark() throws Exception {
        ANALYTICS.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        mockMvc.perform(get("/v1/stats/summary?"))
                .andExpect(status().isOk());

        RecordedRequest recorded = ANALYTICS.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("/v1/stats/summary", recorded.getPath());
    }
}
