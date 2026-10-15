package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.TimeseriesResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.repository.TimeseriesBucketQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeseriesServiceTest {

    @Mock
    private TimeseriesBucketQuery timeseriesBucketQuery;

    private TimeseriesService timeseriesService;

    @BeforeEach
    void setUp() {
        timeseriesService = new TimeseriesService(timeseriesBucketQuery);
    }

    @Test
    void mapsSupportedIntervalToPostgresDuration() {
        when(timeseriesBucketQuery.query("5 minutes", 1L, null, null)).thenReturn(List.of(
                new SecurityEventRepository.TimeBucketAggregate() {
                    @Override
                    public Instant getBucket() {
                        return Instant.parse("2026-05-20T14:00:00Z");
                    }

                    @Override
                    public Long getCount() {
                        return 3L;
                    }
                }));
        TimeseriesResponse response = timeseriesService.timeseries("5m", 1L, null, null);
        assertEquals("5m", response.getInterval());
        assertEquals(1, response.getPoints().size());
        verify(timeseriesBucketQuery).query("5 minutes", 1L, null, null);
    }

    @Test
    void rejectsUnknownInterval() {
        assertThrows(IllegalArgumentException.class, () -> timeseriesService.timeseries("2m", null, null, null));
    }
}
