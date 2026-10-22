package com.wsa.e2e;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

/**
 * E2E side-channel for ingest outbox observation and failure fixtures (raw JDBC).
 * Not the application write path — production durable accepts go through
 * {@code EventIngestService} + JPA; harness readiness uses HTTP health, not this class.
 */
final class OutboxProbe {

    static final String STATUS_PUBLISHED = "PUBLISHED";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_FAILED = "FAILED";

    private static final String STATUS_BY_EVENT_ID =
            "SELECT status FROM ingest_outbox WHERE event_id = ?";
    private static final String COUNT_BY_STATUS =
            "SELECT COUNT(*) FROM ingest_outbox WHERE status = ?";
    private static final String INSERT_PENDING =
            "INSERT INTO ingest_outbox (event_id, payload, status, created_at, attempt_count) "
                    + "VALUES (?, ?, 'PENDING', ?, ?)";

    private final PostgreSQLContainer<?> postgres;

    OutboxProbe(PostgreSQLContainer<?> postgres) {
        this.postgres = postgres;
    }

    String statusOrNull(String eventId) throws Exception {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(STATUS_BY_EVENT_ID)) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    long countByStatus(String status) throws Exception {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_STATUS)) {
            statement.setString(1, status);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    boolean allPublished(Collection<String> eventIds) throws Exception {
        for (String eventId : eventIds) {
            if (!STATUS_PUBLISHED.equals(statusOrNull(eventId))) {
                return false;
            }
        }
        return true;
    }

    /** Inserts a PENDING row used to force relay failure (bad JSON payload). */
    void insertPending(String eventId, String payload, int attemptCount) throws Exception {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(INSERT_PENDING)) {
            statement.setString(1, eventId);
            statement.setString(2, payload);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.setInt(4, attemptCount);
            statement.executeUpdate();
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
