package com.wsa.e2e;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Direct Kafka produce/consume helpers for DLQ and lag assertions. */
final class KafkaProbe {

    private final String bootstrapServers;
    private final String topicRaw;
    private final String topicDlq;

    KafkaProbe(String bootstrapServers, String topicRaw, String topicDlq) {
        this.bootstrapServers = bootstrapServers;
        this.topicRaw = topicRaw;
        this.topicDlq = topicDlq;
    }

    void produceRawBytes(String key, byte[] payload) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topicRaw, key, payload)).get(30, TimeUnit.SECONDS);
        }
    }

    Optional<ConsumerRecord<String, String>> awaitDlqRecord(String key, long timeoutSeconds) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-dlq-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topicDlq));
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        return Optional.of(record);
                    }
                }
            }
        }
        return Optional.empty();
    }

    boolean hasDlqRecord(String key, long timeoutSeconds) throws Exception {
        return awaitDlqRecord(key, timeoutSeconds).isPresent();
    }

    long consumerLag(String groupId) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            ListConsumerGroupOffsetsResult offsetsResult = admin.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    offsetsResult.partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);

            List<TopicPartition> partitions = new ArrayList<>();
            for (TopicPartition tp : committed.keySet()) {
                if (topicRaw.equals(tp.topic())) {
                    partitions.add(tp);
                }
            }
            if (partitions.isEmpty()) {
                // Group may not have committed yet — treat as no lag once end offsets exist with no commits.
                Properties consumerProps = new Properties();
                consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-lag-probe-" + UUID.randomUUID());
                consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                    List<TopicPartition> rawPartitions = consumer.partitionsFor(topicRaw).stream()
                            .map(info -> new TopicPartition(topicRaw, info.partition()))
                            .toList();
                    if (rawPartitions.isEmpty()) {
                        return 0L;
                    }
                    Map<TopicPartition, Long> ends = consumer.endOffsets(rawPartitions);
                    long lag = 0L;
                    for (Long end : ends.values()) {
                        lag += end == null ? 0L : end;
                    }
                    return lag;
                }
            }

            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-lag-probe-" + UUID.randomUUID());
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
                long lag = 0L;
                for (TopicPartition tp : partitions) {
                    OffsetAndMetadata meta = committed.get(tp);
                    long committedOffset = meta == null ? 0L : meta.offset();
                    long end = ends.getOrDefault(tp, 0L);
                    lag += Math.max(0L, end - committedOffset);
                }
                return lag;
            }
        }
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
