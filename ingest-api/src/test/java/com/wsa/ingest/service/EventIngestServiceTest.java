package com.wsa.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.exception.IngestValidationException;
import com.wsa.ingest.repository.IngestOutboxRepository;
import com.wsa.ingest.support.EventFixtures;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventIngestServiceTest {

    @Mock
    private IngestOutboxRepository outboxRepository;

    private EventIngestService service;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new EventIngestService(validator, outboxRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void nullListRejected() {
        IngestValidationException ex = assertThrows(IngestValidationException.class, () -> service.ingest(null));
        assertEquals(400, ex.getHttpStatus());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void emptyListRejected() {
        assertThrows(IngestValidationException.class, () -> service.ingest(List.of()));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void nullElementInListRejected() {
        List<SecurityEventMessage> events = new ArrayList<>();
        events.add(null);
        IngestValidationException ex = assertThrows(IngestValidationException.class, () -> service.ingest(events));
        assertEquals("body", ex.toResponse().getDetails().get(0).getField());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void validEventWritesPendingOutbox() {
        when(outboxRepository.existsByEventId("evt-ut-1")).thenReturn(false);

        service.ingest(List.of(EventFixtures.validEvent("evt-ut-1")));

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("evt-ut-1", captor.getValue().getEventId());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPayload());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void batchWritesEachPendingRow() {
        when(outboxRepository.existsByEventId(any())).thenReturn(false);

        service.ingest(List.of(
                EventFixtures.validEvent("evt-a"),
                EventFixtures.validEvent("evt-b")));

        verify(outboxRepository, times(2)).save(any(IngestOutboxEntity.class));
    }

    @Test
    void existingEventIdIsIdempotentSkip() {
        when(outboxRepository.existsByEventId("evt-ut-1")).thenReturn(true);

        service.ingest(List.of(EventFixtures.validEvent("evt-ut-1")));

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void serializeFailureWrapsAsIllegalState() throws Exception {
        ObjectMapper failing = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {
        });
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        EventIngestService failingService = new EventIngestService(validator, outboxRepository, failing);
        when(outboxRepository.existsByEventId(any())).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> failingService.ingest(List.of(EventFixtures.validEvent("evt-ser"))));
    }
}
