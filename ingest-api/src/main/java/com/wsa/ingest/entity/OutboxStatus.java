package com.wsa.ingest.entity;

/**
 * Lifecycle of a durable ingest outbox row.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
