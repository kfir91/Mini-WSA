package com.miniwsa.analytics.repository;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SecurityEventRepository extends JpaRepository<SecurityEventEntity, String> {

    long countByClientIpAndEventTimestampGreaterThanEqual(String clientIp, Instant since);
}
