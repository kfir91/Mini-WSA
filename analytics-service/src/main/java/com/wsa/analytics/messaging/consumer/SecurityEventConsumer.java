package com.wsa.analytics.messaging.consumer;

import com.wsa.analytics.service.EventEnrichmentService;
import com.wsa.contracts.SecurityEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class SecurityEventConsumer {

    private final EventEnrichmentService eventEnrichmentService;

    @KafkaListener(topics = "${wsa.kafka.topic-raw}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(SecurityEventMessage message) {
        eventEnrichmentService.enrichAndStore(message);
    }
}
