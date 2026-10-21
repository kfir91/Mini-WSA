package com.wsa.analytics.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * DLQ publish adapter. Waits for broker ack so a failed DLQ send does not advance the consumer offset.
 */
@Component
@Profile("!test")
public class DlqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String dlqTopic;
    private final long publishTimeoutSeconds;

    public DlqPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${wsa.kafka.topic-dlq}") String dlqTopic,
            @Value("${wsa.kafka.dlq-publish-timeout-seconds:10}") long publishTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqTopic = dlqTopic;
        this.publishTimeoutSeconds = publishTimeoutSeconds;
    }

    public void publish(ConsumerRecord<?, ?> record, Exception error) {
        String payload = serialize(record.value());
        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, String.valueOf(record.key()), payload);
        dlqRecord.headers().add("error", error.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        dlqRecord.headers().add("original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
        dlqRecord.headers().add("original-partition", String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8));
        dlqRecord.headers().add("original-offset", String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(dlqRecord).get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish to DLQ topic " + dlqTopic, ex);
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
