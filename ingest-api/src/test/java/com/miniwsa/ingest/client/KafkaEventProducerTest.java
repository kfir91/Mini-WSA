package com.miniwsa.ingest.client;

import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.exception.EventPublishException;
import com.miniwsa.ingest.support.EventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventProducerTest {

    @Mock
    private KafkaTemplate<String, SecurityEventMessage> kafkaTemplate;

    private KafkaEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new KafkaEventProducer(kafkaTemplate, "security-events.raw", 2L);
    }

    @Test
    void publishSendsToTopic() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-1");
        @SuppressWarnings("unchecked")
        SendResult<String, SecurityEventMessage> result = mock(SendResult.class);
        when(kafkaTemplate.send(eq("security-events.raw"), eq("evt-1"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(result));

        producer.publish(event);

        verify(kafkaTemplate).send("security-events.raw", "evt-1", event);
    }

    @Test
    void publishWrapsFailure() {
        SecurityEventMessage event = EventFixtures.validEvent("evt-1");
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("slow")));

        assertThrows(EventPublishException.class, () -> producer.publish(event));
    }
}
