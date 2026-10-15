package com.miniwsa.analytics.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("!test")
public class PostgresTimeseriesBucketQuery implements TimeseriesBucketQuery {

    private final SecurityEventRepository repository;

    public PostgresTimeseriesBucketQuery(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SecurityEventRepository.TimeBucketAggregate> query(
            String pgInterval, Long configId, Instant from, Instant to) {
        return repository.timeseries(pgInterval, configId, from, to);
    }
}
