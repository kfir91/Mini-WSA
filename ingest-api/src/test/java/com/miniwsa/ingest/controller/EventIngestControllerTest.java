package com.miniwsa.ingest.controller;

import com.miniwsa.ingest.exception.EventPublishException;
import com.miniwsa.ingest.exception.GlobalExceptionHandler;
import com.miniwsa.ingest.service.EventIngestService;
import com.miniwsa.ingest.support.EventFixtures;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventIngestController.class)
@Import(GlobalExceptionHandler.class)
class EventIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventIngestService eventIngestService;

    @Test
    void singleEventReturns201() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void arrayReturns201() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + EventFixtures.validJson() + "]"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brokerFailureReturns503() throws Exception {
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(eventIngestService).ingest(anyList());
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EventFixtures.validJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BROKER_UNAVAILABLE"));
    }
}
