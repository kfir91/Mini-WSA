package com.miniwsa.analytics.repository;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SecurityEventRepository extends JpaRepository<SecurityEventEntity, String>, JpaSpecificationExecutor<SecurityEventEntity> {

    long countByClientIpAndEventTimestampGreaterThanEqual(String clientIp, Instant since);

    default long countDistinctClientIps(Specification<SecurityEventEntity> spec) {
        return findAll(spec).stream().map(SecurityEventEntity::getClientIp).distinct().count();
    }

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
