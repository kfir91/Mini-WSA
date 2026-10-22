package com.wsa.ingest.api;

import lombok.Builder;

import java.time.Instant;

/**
 * GET /v1/events/{eventId}/status body — outbox projection for the hub/clients.
 */
@Builder
public record IngestEventStatus(
        String eventId,
        IngestClientStatus status,
        int attemptCount,
        Instant createdAt,
        Instant publishedAt,
        String lastError
) {
}
