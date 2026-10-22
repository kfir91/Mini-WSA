package com.wsa.client.web;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoEventSeedControllerTest {

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
    void seedsEventsViaHubEndpoint() throws Exception {
        INGEST.enqueue(new MockResponse().setResponseCode(201));

        mockMvc.perform(post("/hub/seed/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2,\"configId\":14227}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.configId").value(14227));
    }

    @Test
    void rejectsInvalidCount() throws Exception {
        mockMvc.perform(post("/hub/seed/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
