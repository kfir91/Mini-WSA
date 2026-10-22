package com.wsa.ingest.service;

import com.wsa.ingest.api.IngestClientStatus;
import com.wsa.ingest.api.IngestEventStatus;
import com.wsa.ingest.entity.IngestOutboxEntity;

/**
 * Maps durable outbox rows to client-visible ingest statuses.
 */
final class IngestStatusMapper {

    private IngestStatusMapper() {
    }

    static IngestClientStatus toClientStatus(IngestOutboxEntity row) {
        return switch (row.getStatus()) {
            case PUBLISHED -> IngestClientStatus.DONE;
            case FAILED -> IngestClientStatus.FAILED;
            case PENDING -> row.getAttemptCount() > 0
                    ? IngestClientStatus.PROGRESSING
                    : IngestClientStatus.ACCEPTED;
        };
    }

    static IngestEventStatus toEventStatus(IngestOutboxEntity row) {
        return IngestEventStatus.builder()
                .eventId(row.getEventId())
                .status(toClientStatus(row))
                .attemptCount(row.getAttemptCount())
                .createdAt(row.getCreatedAt())
                .publishedAt(row.getPublishedAt())
                .lastError(row.getLastError())
                .build();
    }

    static IngestClientStatus forNewPending() {
        return IngestClientStatus.ACCEPTED;
    }
}
