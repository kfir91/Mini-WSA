package com.wsa.ingest.client;

import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.exception.EventPublishException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka produce adapter. Config is constructor-injected ({@code @Value} on parameters).
 */
@Component
public class KafkaEventProducer {

    private final KafkaTemplate<String, SecurityEventMessage> kafkaTemplate;
    private final String topic;
    private final long publishTimeoutSeconds;

    public KafkaEventProducer(
            KafkaTemplate<String, SecurityEventMessage> kafkaTemplate,
            @Value("${wsa.kafka.topic-raw}") String topic,
            @Value("${wsa.kafka.publish-timeout-seconds}") long publishTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeoutSeconds = publishTimeoutSeconds;
    }

    public void publish(SecurityEventMessage event) {
        try {
            kafkaTemplate.send(topic, event.getEventId(), event)
                    .get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new EventPublishException("Failed to publish security event", ex);
        }
    }
}
