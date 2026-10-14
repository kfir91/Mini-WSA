package com.miniwsa.analytics.messaging.producer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DlqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String dlqTopic;

    public DlqPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${miniwsa.kafka.topic-dlq}") String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = dlqTopic;
    }

    public void publish(ConsumerRecord<?, ?> record, Exception error) {
        String payload = record.value() == null ? "" : record.value().toString();
        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, String.valueOf(record.key()), payload);
        dlqRecord.headers().add("error", error.getClass().getSimpleName().getBytes());
        kafkaTemplate.send(dlqRecord);
    }
}
