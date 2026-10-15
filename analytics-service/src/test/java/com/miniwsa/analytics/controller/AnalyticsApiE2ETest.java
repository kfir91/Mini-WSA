package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.repository.AlertDefinitionRepository;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:miniwsa-e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository repository;

    @Autowired
    private AlertDefinitionRepository alertDefinitionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        alertDefinitionRepository.deleteAll();
        Instant now = Instant.now();
        repository.save(SecurityEventFixtures.event(
                "evt-a", "INJECTION", "DENY", 80, now.minus(Duration.ofMinutes(30))));
        repository.save(SecurityEventFixtures.event(
                "evt-b", "XSS", "ALERT", 40, now.minus(Duration.ofMinutes(20))));
        repository.save(SecurityEventFixtures.event(
                "evt-c", "INJECTION", "MONITOR", 20, now.minus(Duration.ofMinutes(10))));
    }

    @Test
    void samplesFiltersByCategoryAndPaginates() throws Exception {
        mockMvc.perform(get("/v1/events/samples")
                        .param("category", "INJECTION")
                        .param("limit", "1")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.items[0].category").value("INJECTION"))
                .andExpect(jsonPath("$.items[0].attackType").exists())
                .andExpect(jsonPath("$.items[0].threatScore").exists())
                .andExpect(jsonPath("$.items[0].receivedAt").exists());
    }

    @Test
    void samplesFiltersByMinThreatScore() throws Exception {
        mockMvc.perform(get("/v1/events/samples").param("minThreatScore", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("evt-a"));
    }

    @Test
    void summaryReturnsAggregates() throws Exception {
        mockMvc.perform(get("/v1/stats/summary").param("configId", "14227"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.configId").value(14227))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.byAction.DENY").value(1))
                .andExpect(jsonPath("$.topAttackers", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.topTargetedPaths", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void timeseriesReturnsBuckets() throws Exception {
        mockMvc.perform(get("/v1/stats/timeseries")
                        .param("interval", "5m")
                        .param("configId", "14227"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("5m"))
                .andExpect(jsonPath("$.points", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.points[0].bucket").exists())
                .andExpect(jsonPath("$.points[0].count").exists());
    }

    @Test
    void timeseriesRejectsInvalidInterval() throws Exception {
        mockMvc.perform(get("/v1/stats/timeseries").param("interval", "2m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void alertsDefineAndEvaluate() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 14227,
                                  "category": "INJECTION",
                                  "metric": "event_count",
                                  "window": "1h",
                                  "threshold": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.metric").value("event_count"))
                .andExpect(jsonPath("$.window").value("1h"))
                .andExpect(jsonPath("$.threshold").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String alertId = body.get("id").asText();

        assertTrue(alertDefinitionRepository.findById(alertId).isPresent());

        mockMvc.perform(get("/v1/alerts/evaluate").param("alertId", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value(alertId))
                .andExpect(jsonPath("$[0].firing").value(true))
                .andExpect(jsonPath("$[0].value").value(2))
                .andExpect(jsonPath("$[0].threshold").value(2));
    }

    @Test
    void alertsEvaluateAllReturnsEveryPersistedRule() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 14227,
                                  "metric": "event_count",
                                  "window": "1h",
                                  "threshold": 1
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 14227,
                                  "metric": "unique_ips",
                                  "window": "1h",
                                  "threshold": 1
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void alertsRejectInvalidBody() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "metric": "",
                                  "window": "5m",
                                  "threshold": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void alertsUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/v1/alerts/evaluate").param("alertId", "missing-alert"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
