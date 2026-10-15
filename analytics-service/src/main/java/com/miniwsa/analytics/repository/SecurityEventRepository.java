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
              AND (:category IS NULL OR e.category = :category)
              AND e.eventTimestamp >= :since
            """)
    long countSince(
            @Param("configId") Long configId, @Param("category") String category, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(DISTINCT e.clientIp) FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND e.eventTimestamp >= :since
            """)
    long countDistinctIpsSince(@Param("configId") Long configId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(e) FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND (:fromTs IS NULL OR e.eventTimestamp >= :fromTs)
              AND (:toTs IS NULL OR e.eventTimestamp <= :toTs)
            """)
    long countForSummary(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("""
            SELECT e.category AS category, COUNT(e) AS count, AVG(e.threatScore) AS avgThreatScore
            FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND (:fromTs IS NULL OR e.eventTimestamp >= :fromTs)
              AND (:toTs IS NULL OR e.eventTimestamp <= :toTs)
            GROUP BY e.category
            """)
    List<CategoryAggregate> aggregateByCategory(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("""
            SELECT e.action AS action, COUNT(e) AS count
            FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND (:fromTs IS NULL OR e.eventTimestamp >= :fromTs)
              AND (:toTs IS NULL OR e.eventTimestamp <= :toTs)
            GROUP BY e.action
            """)
    List<ActionAggregate> aggregateByAction(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("""
            SELECT e.clientIp AS clientIp, COUNT(e) AS count, AVG(e.threatScore) AS avgThreatScore
            FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND (:fromTs IS NULL OR e.eventTimestamp >= :fromTs)
              AND (:toTs IS NULL OR e.eventTimestamp <= :toTs)
            GROUP BY e.clientIp
            ORDER BY COUNT(e) DESC
            LIMIT 10
            """)
    List<AttackerAggregate> topAttackers(@Param("configId") Long configId, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("""
            SELECT e.path AS path, COUNT(e) AS count
            FROM SecurityEventEntity e
            WHERE (:configId IS NULL OR e.configId = :configId)
              AND (:fromTs IS NULL OR e.eventTimestamp >= :fromTs)
              AND (:toTs IS NULL OR e.eventTimestamp <= :toTs)
            GROUP BY e.path
            ORDER BY COUNT(e) DESC
            LIMIT 10
            """)
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
