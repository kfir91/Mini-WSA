package com.wsa.ingest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.wsa.ingest.exception.IngestValidationException;
import com.wsa.ingest.service.EventIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventIngestControllerTest {

    @Mock
    private EventIngestService eventIngestService;

    private EventIngestController controller;

    @BeforeEach
    void setUp() {
        controller = new EventIngestController(eventIngestService, new ObjectMapper());
    }

    @Test
    void nullBodyRejected() {
        assertThrows(IngestValidationException.class, () -> controller.ingestEvents(null));
        verify(eventIngestService, never()).ingest(anyList());
    }

    @Test
    void missingNodeRejected() {
        assertThrows(IngestValidationException.class, () -> controller.ingestEvents(MissingNode.getInstance()));
        verify(eventIngestService, never()).ingest(anyList());
    }
}
