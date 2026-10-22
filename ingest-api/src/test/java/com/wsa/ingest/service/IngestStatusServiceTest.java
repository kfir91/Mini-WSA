package com.wsa.ingest.service;

import com.wsa.contracts.ApiException;
import com.wsa.ingest.api.IngestClientStatus;
import com.wsa.ingest.api.IngestEventStatus;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.repository.IngestOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestStatusServiceTest {

    @Mock
    private IngestOutboxRepository outboxRepository;

    @InjectMocks
    private IngestStatusService service;

    @Test
    void returnsMappedStatus() {
        when(outboxRepository.findByEventId("evt-1")).thenReturn(Optional.of(
                IngestOutboxEntity.builder()
                        .eventId("evt-1")
                        .payload("{}")
                        .status(OutboxStatus.PUBLISHED)
                        .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .publishedAt(Instant.parse("2026-01-01T00:00:01Z"))
                        .attemptCount(1)
                        .build()));

        IngestEventStatus status = service.getStatus("evt-1");
        assertEquals(IngestClientStatus.DONE, status.status());
        assertEquals(1, status.attemptCount());
    }

    @Test
    void unknownEventIdIsNotFound() {
        when(outboxRepository.findByEventId("missing")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.getStatus("missing"));
    }
}
