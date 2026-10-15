package com.miniwsa.analytics.messaging.consumer;

import com.miniwsa.analytics.service.EventEnrichmentService;
import com.miniwsa.contracts.SecurityEventMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SecurityEventConsumer {

    private final EventEnrichmentService eventEnrichmentService;

    public SecurityEventConsumer(EventEnrichmentService eventEnrichmentService) {
        this.eventEnrichmentService = eventEnrichmentService;
    }

    @KafkaListener(topics = "${miniwsa.kafka.topic-raw}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(SecurityEventMessage message) {
        eventEnrichmentService.enrichAndStore(message);
    }
}
