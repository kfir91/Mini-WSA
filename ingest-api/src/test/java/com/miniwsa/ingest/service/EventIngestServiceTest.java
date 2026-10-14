package com.miniwsa.ingest.service;

import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.client.KafkaEventProducer;
import com.miniwsa.ingest.exception.EventPublishException;
import com.miniwsa.ingest.exception.IngestValidationException;
import com.miniwsa.ingest.support.EventFixtures;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void stampsReceivedAtAndPublishesSingleEvent() {
        SecurityEventMessage event = EventFixtures.validEvent("evt-1");
        service.ingest(List.of(event));
        assertNotNull(event.getReceivedAt());
        verify(producer).publish(event);
    }

    @Test
    void validBatchPublishesEachEvent() {
        SecurityEventMessage first = EventFixtures.validEvent("evt-1");
        SecurityEventMessage second = EventFixtures.validEvent("evt-2");
        service.ingest(List.of(first, second));
        verify(producer, times(2)).publish(any());
    }

    @Test
    void invalidItemPublishesNothing() {
        SecurityEventMessage invalid = EventFixtures.validEvent("evt-bad");
        invalid.setEventId(" ");
        assertThrows(IngestValidationException.class, () -> service.ingest(List.of(EventFixtures.validEvent("evt-1"), invalid)));
        verify(producer, never()).publish(any());
    }

    @Test
    void producerFailureIsPropagated() {
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(producer).publish(any());
        assertThrows(EventPublishException.class, () -> service.ingest(List.of(EventFixtures.validEvent("evt-1"))));
    }
}
