package com.wsa.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class PostgresTimeseriesBucketQuery implements TimeseriesBucketQuery {

    private final SecurityEventRepository repository;

    @Override
    public List<SecurityEventRepository.TimeBucketAggregate> query(
            String pgInterval, Long configId, Instant from, Instant to) {
        return repository.timeseries(pgInterval, configId, from, to);
    }
}
