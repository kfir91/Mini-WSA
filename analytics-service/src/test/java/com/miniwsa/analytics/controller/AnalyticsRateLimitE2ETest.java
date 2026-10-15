package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.support.SecurityEventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:miniwsa-rl;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "miniwsa.rate-limit.requests-per-minute=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsRateLimitE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(SecurityEventFixtures.event(
                "evt-rl", "INJECTION", "DENY", 50, Instant.parse("2026-05-20T14:00:00Z")));
    }

    @Test
    void statsAndSamplesAreRateLimitedButAlertsAreNot() throws Exception {
        mockMvc.perform(get("/v1/stats/summary")
                        .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/stats/summary")
                        .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        mockMvc.perform(get("/v1/events/samples"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 14227,
                                  "metric": "event_count",
                                  "window": "5m",
                                  "threshold": 10
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
