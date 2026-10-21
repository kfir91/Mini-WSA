package com.wsa.analytics.repository;

import java.time.Instant;
import java.util.List;

/**
 * Time-bucket aggregation for stats timeseries. Production uses Postgres {@code date_bin};
 * the test profile uses an in-memory implementation compatible with H2.
 */
public interface TimeseriesBucketQuery {

    List<SecurityEventRepository.TimeBucketAggregate> query(
            String pgInterval, Long configId, Instant from, Instant to);
}
