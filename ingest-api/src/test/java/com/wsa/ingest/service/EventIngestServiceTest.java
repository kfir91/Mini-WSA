package com.wsa.ingest.service;

import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.client.KafkaEventProducer;
import com.wsa.ingest.exception.IngestValidationException;
import com.wsa.ingest.support.EventFixtures;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventIngestServiceTest {

    @Mock
    private KafkaEventProducer producer;

    private EventIngestService service;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new EventIngestService(validator, producer);
    }

    @Test
    void nullListRejected() {
        IngestValidationException ex = assertThrows(IngestValidationException.class, () -> service.ingest(null));
        assertEquals(400, ex.getHttpStatus());
        verify(producer, never()).publish(any());
    }

    @Test
    void emptyListRejected() {
        assertThrows(IngestValidationException.class, () -> service.ingest(List.of()));
        verify(producer, never()).publish(any());
    }

    @Test
    void nullElementInListRejected() {
        List<SecurityEventMessage> events = new ArrayList<>();
        events.add(null);
        IngestValidationException ex = assertThrows(IngestValidationException.class, () -> service.ingest(events));
        assertEquals("body", ex.toResponse().getDetails().get(0).getField());
        verify(producer, never()).publish(any());
    }

    @Test
    void validEventPublishes() {
        service.ingest(List.of(EventFixtures.validEvent("evt-ut-1")));
        verify(producer).publish(any(SecurityEventMessage.class));
    }
}
