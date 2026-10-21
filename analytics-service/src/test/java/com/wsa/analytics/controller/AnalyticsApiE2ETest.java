package com.wsa.analytics.controller;

import com.wsa.analytics.repository.AlertDefinitionRepository;
import com.wsa.analytics.repository.SecurityEventRepository;
import com.wsa.analytics.support.ClasspathJson;
import com.wsa.analytics.support.SecurityEventFixtures;
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
        "spring.datasource.url=jdbc:h2:mem:wsa-e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
    void timeseriesSupportsOneMinuteAndOneHour() throws Exception {
        mockMvc.perform(get("/v1/stats/timeseries").param("interval", "1m").param("configId", "14227"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1m"));
        mockMvc.perform(get("/v1/stats/timeseries").param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1h"));
    }

    @Test
    void samplesFiltersByActionAndTimeRange() throws Exception {
        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now().plus(Duration.ofMinutes(1));
        mockMvc.perform(get("/v1/events/samples")
                        .param("action", "DENY")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].action").value("DENY"));
    }

    @Test
    void summaryWithoutConfigIdAggregatesAll() throws Exception {
        mockMvc.perform(get("/v1/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(3));
    }

    @Test
    void alertsDefineAndEvaluate() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-injection-1h-t2.json")))
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
                .andExpect(jsonPath("$[0].name").value("INJECTION · event_count ≥2 in 1h · config 14227"))
                .andExpect(jsonPath("$[0].metric").value("event_count"))
                .andExpect(jsonPath("$[0].window").value("1h"))
                .andExpect(jsonPath("$[0].category").value("INJECTION"))
                .andExpect(jsonPath("$[0].configId").value(14227))
                .andExpect(jsonPath("$[0].firing").value(true))
                .andExpect(jsonPath("$[0].value").value(2))
                .andExpect(jsonPath("$[0].threshold").value(2));
    }

    @Test
    void alertsEvaluateAllReturnsEveryPersistedRule() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-event-count-1h.json")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-unique-ips-1h.json")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void alertsRejectInvalidBody() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-empty-metric.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void alertsUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/v1/alerts/evaluate").param("alertId", "missing-alert"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void alertsRejectUnknownCategory() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-unknown-category.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void alertsRejectDuplicateRule() throws Exception {
        String body = ClasspathJson.load("fixtures/alerts/define-xss-5m-t7.json");
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(com.wsa.contracts.ErrorMessage.DUPLICATE_ALERT_RULE.getValue()));
    }

    @Test
    void alertsBlankCategoryPersistsAsNull() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-blank-category.json")))
                .andExpect(status().isCreated())
                .andReturn();
        String alertId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        assertTrue(alertDefinitionRepository.findById(alertId).isPresent());
        org.junit.jupiter.api.Assertions.assertNull(
                alertDefinitionRepository.findById(alertId).orElseThrow().getCategory());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void alertsDefineUnreadableBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void alertsEvaluateBlankAlertIdEvaluatesAll() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/alerts/define-event-count-1h.json")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/alerts/evaluate").param("alertId", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void demoAlertsFireWithSeededEvents() throws Exception {
        mockMvc.perform(post("/demo/alerts/fire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void samplesEmptyWhenNoMatches() throws Exception {
        mockMvc.perform(get("/v1/events/samples")
                        .param("category", "RATE_LIMIT")
                        .param("minThreatScore", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void timeseriesFiltersByTimeRangeAndConfig() throws Exception {
        Instant from = Instant.now().minus(Duration.ofHours(2));
        Instant to = Instant.now().plus(Duration.ofMinutes(1));
        mockMvc.perform(get("/v1/stats/timeseries")
                        .param("interval", "5m")
                        .param("configId", "14227")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void adminerGoConfigEndpoint() throws Exception {
        mockMvc.perform(get("/adminer-go/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver").value("pgsql"))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.database").exists());
    }
}
