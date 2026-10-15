package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.TimeseriesResponse;
import com.miniwsa.analytics.repository.SecurityEventRepository;
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
    private SecurityEventRepository repository;

    private TimeseriesService timeseriesService;

    @BeforeEach
    void setUp() {
        timeseriesService = new TimeseriesService(repository);
    }

    @Test
    void mapsSupportedIntervalToPostgresDuration() {
        Instant bucket = Instant.parse("2026-05-20T14:00:00Z");
        when(repository.timeseries("5 minutes", 1L, null, null)).thenReturn(List.of(new SecurityEventRepository.TimeBucketAggregate() {
            @Override
            public Instant getBucket() {
                return bucket;
            }

            @Override
            public Long getCount() {
                return 4L;
            }
        }));
        TimeseriesResponse response = timeseriesService.timeseries("5m", 1L, null, null);
        assertEquals("5m", response.getInterval());
        assertEquals(4L, response.getPoints().get(0).getCount());
        verify(repository).timeseries("5 minutes", 1L, null, null);
    }

    @Test
    void rejectsUnknownInterval() {
        assertThrows(IllegalArgumentException.class, () -> timeseriesService.timeseries("2m", null, null, null));
    }
}
