package com.wsa.ingest.repository;

import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IngestOutboxRepository extends JpaRepository<IngestOutboxEntity, Long> {

    boolean existsByEventId(String eventId);

    long countByStatus(OutboxStatus status);

    /**
     * Locks the next PENDING rows for relay (Postgres / H2 PostgreSQL mode).
     * Must run inside a transaction; caller should commit before Kafka publish.
     */
    @Query(value = """
            SELECT * FROM ingest_outbox
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<IngestOutboxEntity> lockNextPending(@Param("limit") int limit);
}
