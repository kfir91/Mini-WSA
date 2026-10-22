package com.wsa.ingest.service;

import com.wsa.ingest.api.IngestClientStatus;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestStatusMapperTest {

    @Test
    void pendingWithNoAttemptsIsAccepted() {
        assertEquals(IngestClientStatus.ACCEPTED, IngestStatusMapper.toClientStatus(row(OutboxStatus.PENDING, 0)));
    }

    @Test
    void pendingWithAttemptsIsProgressing() {
        assertEquals(IngestClientStatus.PROGRESSING, IngestStatusMapper.toClientStatus(row(OutboxStatus.PENDING, 2)));
    }

    @Test
    void publishedIsDone() {
        assertEquals(IngestClientStatus.DONE, IngestStatusMapper.toClientStatus(row(OutboxStatus.PUBLISHED, 1)));
    }

    @Test
    void failedMapsToFailed() {
        assertEquals(IngestClientStatus.FAILED, IngestStatusMapper.toClientStatus(row(OutboxStatus.FAILED, 5)));
    }

    private static IngestOutboxEntity row(OutboxStatus status, int attempts) {
        return IngestOutboxEntity.builder()
                .eventId("evt-1")
                .payload("{}")
                .status(status)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .attemptCount(attempts)
                .build();
    }
}
