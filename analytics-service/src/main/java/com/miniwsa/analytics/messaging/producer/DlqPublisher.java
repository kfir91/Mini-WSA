package com.miniwsa.analytics.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class DlqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${miniwsa.kafka.topic-dlq}")
    private String dlqTopic;

    public void publish(ConsumerRecord<?, ?> record, Exception error) {
        String payload = serialize(record.value());
        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, String.valueOf(record.key()), payload);
        dlqRecord.headers().add("error", error.getClass().getSimpleName().getBytes());
        kafkaTemplate.send(dlqRecord);
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
