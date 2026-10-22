package com.wsa.ingest.api;

import lombok.Builder;

import java.util.List;

/**
 * POST /v1/events/ingest 201 body.
 */
@Builder
public record IngestAcceptResponse(List<IngestAcceptItem> items) {
}
