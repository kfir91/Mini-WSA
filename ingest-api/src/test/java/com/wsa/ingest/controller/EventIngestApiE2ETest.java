package com.wsa.ingest.controller;

import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.client.KafkaEventProducer;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.exception.EventPublishException;
import com.wsa.ingest.repository.IngestOutboxRepository;
import com.wsa.ingest.service.OutboxRelayService;
import com.wsa.ingest.support.ClasspathJson;
import com.wsa.ingest.support.EventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wsa_ingest_e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIngestApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestOutboxRepository outboxRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @MockBean
    private KafkaTemplate<String, SecurityEventMessage> kafkaTemplate;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void singleValidEventReturns201AndWritesPendingOutbox() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());

        verify(kafkaEventProducer, never()).publish(any());
        List<IngestOutboxEntity> rows = outboxRepository.findAll();
        assertEquals(1, rows.size());
        assertEquals(OutboxStatus.PENDING, rows.get(0).getStatus());
        assertTrue(rows.get(0).getPayload().contains("receivedAt")
                || rows.get(0).getPayload().contains("received_at")
                || rows.get(0).getPayload().contains("evt-"));
    }

    @Test
    void arrayReturns201AndWritesEachOutboxRow() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/security-event-batch.json")))
                .andExpect(status().isCreated());

        verify(kafkaEventProducer, never()).publish(any());
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void emptyArrayReturns400AndNoOutboxRows() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/empty-array.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertEquals(0, outboxRepository.count());
    }

    @Test
    void invalidEnumReturns400AndNoOutboxRows() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/security-event-invalid-action.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertEquals(0, outboxRepository.count());
    }

    @Test
    void blankEventIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/security-event-blank-event-id.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rePostSameEventIdIsIdempotent() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());

        assertEquals(1, outboxRepository.count());
    }

    @Test
    void relayPublishesPendingRows() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());

        outboxRelayService.relayBatch();

        verify(kafkaEventProducer, times(1)).publish(any(SecurityEventMessage.class));
        assertEquals(OutboxStatus.PUBLISHED, outboxRepository.findAll().get(0).getStatus());
    }

    @Test
    void relayKeepsPendingWhenBrokerFails() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());

        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(kafkaEventProducer).publish(any());

        outboxRelayService.relayBatch();

        IngestOutboxEntity row = outboxRepository.findAll().get(0);
        assertEquals(OutboxStatus.PENDING, row.getStatus());
        assertEquals(1, row.getAttemptCount());
    }

    @Test
    void nullBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/null-body.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonObjectBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/number-body.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unreadableJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/unreadable-body.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
