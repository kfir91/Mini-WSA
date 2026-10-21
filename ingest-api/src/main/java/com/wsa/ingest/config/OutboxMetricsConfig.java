package com.wsa.ingest.config;

import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.repository.IngestOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Registers {@code ingest_outbox_pending} gauge for ops dashboards.
 */
@Component
@RequiredArgsConstructor
public class OutboxMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final IngestOutboxRepository outboxRepository;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("ingest_outbox_pending", outboxRepository, repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Number of PENDING ingest outbox rows awaiting Kafka relay")
                .register(meterRegistry);
    }
}
