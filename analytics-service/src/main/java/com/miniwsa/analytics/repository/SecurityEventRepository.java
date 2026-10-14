package com.miniwsa.analytics.repository;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SecurityEventRepository extends JpaRepository<SecurityEventEntity, String>, JpaSpecificationExecutor<SecurityEventEntity> {

    long countByClientIpAndEventTimestampGreaterThanEqual(String clientIp, Instant since);

    @Query("""
            SELECT COUNT(e) FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND e.eventTimestamp >= :since
            """)
    long countSince(@Param("configId") Long configId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(DISTINCT e.clientIp) FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND e.eventTimestamp >= :since
            """)
    long countDistinctIpsSince(@Param("configId") Long configId, @Param("since") Instant since);

    @Query(value = """
            SELECT COUNT(*) FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            """, nativeQuery = true)
    long countForSummary(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query(value = """
            SELECT e.category AS category, COUNT(*) AS count, AVG(e.threat_score) AS avgThreatScore
            FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            GROUP BY e.category
            """, nativeQuery = true)
    List<CategoryAggregate> aggregateByCategory(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query(value = """
            SELECT e.action AS action, COUNT(*) AS count
            FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            GROUP BY e.action
            """, nativeQuery = true)
    List<ActionAggregate> aggregateByAction(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query(value = """
            SELECT e.client_ip AS clientIp, COUNT(*) AS count, AVG(e.threat_score) AS avgThreatScore
            FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            GROUP BY e.client_ip
            ORDER BY COUNT(*) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<AttackerAggregate> topAttackers(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query(value = """
            SELECT e.path AS path, COUNT(*) AS count
            FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            GROUP BY e.path
            ORDER BY COUNT(*) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<PathAggregate> topPaths(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query(value = """
            SELECT date_bin(CAST(:bucket AS interval), e.event_timestamp, TIMESTAMP 'epoch') AS bucket, COUNT(*) AS count
            FROM security_events e
            WHERE (CAST(:configId AS bigint) IS NULL OR e.config_id = CAST(:configId AS bigint))
              AND (CAST(:fromTs AS timestamptz) IS NULL OR e.event_timestamp >= CAST(:fromTs AS timestamptz))
              AND (CAST(:toTs AS timestamptz) IS NULL OR e.event_timestamp <= CAST(:toTs AS timestamptz))
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<TimeBucketAggregate> timeseries(
            @Param("bucket") String bucket,
            @Param("configId") Long configId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs);

    interface CategoryAggregate {
        String getCategory();
        Long getCount();
        Double getAvgThreatScore();
    }

    interface ActionAggregate {
        String getAction();
        Long getCount();
    }

    interface AttackerAggregate {
        String getClientIp();
        Long getCount();
        Double getAvgThreatScore();
    }

    interface PathAggregate {
        String getPath();
        Long getCount();
    }

    interface TimeBucketAggregate {
        Instant getBucket();
        Long getCount();
    }
}
