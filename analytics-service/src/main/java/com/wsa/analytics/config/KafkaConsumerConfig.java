package com.wsa.analytics.config;

import com.wsa.analytics.messaging.producer.DlqPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Profile("!test")
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            DlqPublisher dlqPublisher,
            @Value("${wsa.kafka.retry-interval-ms}") long retryIntervalMs,
            @Value("${wsa.kafka.retry-attempts}") long retryAttempts) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> dlqPublisher.publish(record, exception),
                new FixedBackOff(retryIntervalMs, retryAttempts));
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
