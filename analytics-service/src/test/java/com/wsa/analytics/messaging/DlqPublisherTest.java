package com.wsa.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.analytics.messaging.producer.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DlqPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DlqPublisher(kafkaTemplate, new ObjectMapper(), "security-events.dlq", 2L);
    }

    @Test
    void publishStringPayloadWaitsOnSend() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw", 0, 1L, "k1", "payload");
        publisher.publish(record, new IllegalStateException("boom"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("security-events.dlq", captor.getValue().topic());
        assertEquals("k1", captor.getValue().key());
        assertEquals("payload", captor.getValue().value());
        assertTrue(captor.getValue().headers().lastHeader("error") != null);
        assertEquals("raw", new String(captor.getValue().headers().lastHeader("original-topic").value(), StandardCharsets.UTF_8));
        assertEquals("0", new String(captor.getValue().headers().lastHeader("original-partition").value(), StandardCharsets.UTF_8));
        assertEquals("1", new String(captor.getValue().headers().lastHeader("original-offset").value(), StandardCharsets.UTF_8));
    }

    @Test
    void publishFailureThrows() {
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TimeoutException("slow"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw", 0, 1L, "k1", "payload");
        assertThrows(IllegalStateException.class,
                () -> publisher.publish(record, new RuntimeException("orig")));
    }

    @Test
    void publishNullValueUsesEmptyString() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("raw", 0, 1L, "k1", null);
        publisher.publish(record, new RuntimeException("x"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("", captor.getValue().value());
    }

    @Test
    void publishObjectSerializesJson() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("raw", 0, 1L, "k1", java.util.Map.of("a", 1));
        publisher.publish(record, new RuntimeException("x"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertTrue(captor.getValue().value().contains("\"a\""));
    }

    @Test
    void publishUnserializableFallsBackToString() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Object bad = new Object() {
            @Override
            public String toString() {
                return "fallback-obj";
            }
        };
        ObjectMapper failing = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, "nope");
            }
        };
        publisher = new DlqPublisher(kafkaTemplate, failing, "security-events.dlq", 2L);

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("raw", 0, 1L, "k1", bad);
        publisher.publish(record, new RuntimeException("x"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("fallback-obj", captor.getValue().value());
    }
}
