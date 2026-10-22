package com.wsa.ingest.api;

import lombok.Builder;

/**
 * One event accepted (or re-accepted idempotently) into the outbox.
 */
@Builder
public record IngestAcceptItem(String eventId, IngestClientStatus status) {
}
