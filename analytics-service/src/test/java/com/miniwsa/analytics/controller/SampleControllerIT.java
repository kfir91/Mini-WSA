package com.miniwsa.analytics.controller;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SampleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(event("evt-a", "INJECTION", "DENY", 80, Instant.parse("2026-05-20T14:00:00Z")));
        repository.save(event("evt-b", "XSS", "ALERT", 40, Instant.parse("2026-05-20T15:00:00Z")));
        repository.save(event("evt-c", "INJECTION", "MONITOR", 20, Instant.parse("2026-05-20T16:00:00Z")));
    }

    @Test
    void filtersByCategoryAndPaginates() throws Exception {
        mockMvc.perform(get("/v1/events/samples")
                        .param("category", "INJECTION")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items[0].category").value("INJECTION"))
                .andExpect(jsonPath("$.items[0].attackType").exists())
                .andExpect(jsonPath("$.items[0].threatScore").exists())
                .andExpect(jsonPath("$.items[0].receivedAt").exists());
    }

    @Test
    void filtersByMinThreatScore() throws Exception {
        mockMvc.perform(get("/v1/events/samples").param("minThreatScore", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("evt-a"));
    }

    private static SecurityEventEntity event(String id, String category, String action, int score, Instant ts) {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setEventId(id);
        entity.setEventTimestamp(ts);
        entity.setReceivedAt(ts.plusSeconds(1));
        entity.setConfigId(14227L);
        entity.setPolicyId("pol_web1");
        entity.setClientIp("203.0.113.42");
        entity.setHostname("www.example.com");
        entity.setPath("/api/v1/login");
        entity.setMethod("POST");
        entity.setStatusCode(403);
        entity.setUserAgent("Mozilla/5.0");
        entity.setRuleId("950001");
        entity.setRuleName("SQL_INJECTION");
        entity.setRuleMessage("detected");
        entity.setSeverity("CRITICAL");
        entity.setCategory(category);
        entity.setAction(action);
        entity.setCountry("CN");
        entity.setCity("Beijing");
        entity.setRequestSize(1024);
        entity.setResponseSize(256);
        entity.setAttackType("SQL/Command Injection");
        entity.setThreatScore(score);
        return entity;
    }
}
