package com.wsa.ingest.api;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Client-facing ingest lifecycle projected from {@code ingest_outbox}.
 */
public enum IngestClientStatus {
    ACCEPTED("accepted"),
    PROGRESSING("progressing"),
    DONE("done"),
    FAILED("failed");

    private final String wire;

    IngestClientStatus(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }
}
