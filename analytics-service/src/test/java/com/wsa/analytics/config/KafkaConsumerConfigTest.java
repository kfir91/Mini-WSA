package com.wsa.analytics.config;

import com.wsa.analytics.messaging.producer.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ExceptionClassifier;
import org.springframework.kafka.listener.FailedRecordProcessor;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Mock
    private DlqPublisher dlqPublisher;

    @Test
    void errorHandlerRecoversViaDlqPublisher() throws Exception {
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(dlqPublisher, 100L, 0L);
        assertNotNull(handler);

        ConsumerAwareRecordRecoverer recoverer = recovererOf(handler);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("security-events.raw", 0, 7L, "poison-key", "not-json");
        RuntimeException cause = new RuntimeException("boom");
        recoverer.accept(record, null, cause);

        verify(dlqPublisher).publish(record, cause);
    }

    @Test
    void deserializationExceptionIsNotRetryable() throws Exception {
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(dlqPublisher, 100L, 3L);
        DeserializationException deser = new DeserializationException(
                "bad", "{}".getBytes(StandardCharsets.UTF_8), false, null);

        BinaryExceptionClassifier classifier = classifierOf(handler);
        assertFalse(classifier.classify(deser),
                "DeserializationException must be classified as not retryable");
    }

    private static ConsumerAwareRecordRecoverer recovererOf(DefaultErrorHandler handler) throws Exception {
        Method getFailureTracker = FailedRecordProcessor.class.getDeclaredMethod("getFailureTracker");
        getFailureTracker.setAccessible(true);
        Object tracker = getFailureTracker.invoke(handler);
        Method getRecoverer = tracker.getClass().getDeclaredMethod("getRecoverer");
        getRecoverer.setAccessible(true);
        return (ConsumerAwareRecordRecoverer) getRecoverer.invoke(tracker);
    }

    private static BinaryExceptionClassifier classifierOf(DefaultErrorHandler handler) throws Exception {
        Method method = ExceptionClassifier.class.getDeclaredMethod("getClassifier");
        method.setAccessible(true);
        return (BinaryExceptionClassifier) method.invoke(handler);
    }
}
