package com.wsa.e2e;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Read-only check of ingest outbox status (secondary assert, not a production API). */
final class OutboxProbe {

    static final String STATUS_PUBLISHED = "PUBLISHED";

    private static final String STATUS_BY_EVENT_ID =
            "SELECT status FROM ingest_outbox WHERE event_id = ?";

    private final PostgreSQLContainer<?> postgres;

    OutboxProbe(PostgreSQLContainer<?> postgres) {
        this.postgres = postgres;
    }

    String statusOrNull(String eventId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement statement = connection.prepareStatement(STATUS_BY_EVENT_ID)) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
