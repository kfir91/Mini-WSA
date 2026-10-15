package com.miniwsa.ingest.controller;

import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.client.KafkaEventProducer;
import com.miniwsa.ingest.exception.EventPublishException;
import com.miniwsa.ingest.support.ClasspathJson;
import com.miniwsa.ingest.support.EventFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIngestApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    /** Prevents real KafkaTemplate wiring when the producer bean is replaced. */
    @MockBean
    private KafkaTemplate<String, SecurityEventMessage> kafkaTemplate;

    @Test
    void singleValidEventReturns201AndPublishesWithReceivedAt() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());

        ArgumentCaptor<SecurityEventMessage> captor = ArgumentCaptor.forClass(SecurityEventMessage.class);
        verify(kafkaEventProducer).publish(captor.capture());
        assertNotNull(captor.getValue().getReceivedAt());
    }

    @Test
    void arrayReturns201AndPublishesEach() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/security-event-batch.json")))
                .andExpect(status().isCreated());

        verify(kafkaEventProducer, times(2)).publish(any(SecurityEventMessage.class));
    }

    @Test
    void emptyArrayReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/empty-array.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidEnumReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ClasspathJson.load("fixtures/security-event-invalid-action.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
    void brokerFailureReturns503() throws Exception {
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(kafkaEventProducer).publish(any(SecurityEventMessage.class));

        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BROKER_UNAVAILABLE"));
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
