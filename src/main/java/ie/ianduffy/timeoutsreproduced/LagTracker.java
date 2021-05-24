package ie.ianduffy.timeoutsreproduced;

import org.apache.commons.configuration2.Configuration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class LagTracker {

    private static final Logger logger = LoggerFactory.getLogger(LagTracker.class);

    private final AdminClient kafkaAdminClient;
    private final KafkaConsumer kafkaConsumer;
    private final String topic;
    private final String groupId;

    private final List<TopicPartition> partitions;
    private final HashMap<TopicPartition, Long> latestConsumerGroupOffsets = new HashMap<>();
    private final HashMap<TopicPartition, Long> latestLogEndOffsets = new HashMap<>();

    public LagTracker(Configuration configuration) {
        Properties properties = new Properties();
        Iterator<String> kafkaConfigurationKeys = configuration.getKeys("kafka.common");
        while (kafkaConfigurationKeys.hasNext()) {
            String key = kafkaConfigurationKeys.next();
            properties.put(key.replace("kafka.common.", ""), configuration.getProperty(key));
        }

        this.topic = configuration.getString("subject.topic");
        this.groupId = configuration.getString("subject.groupId");

        this.kafkaAdminClient = KafkaAdminClient.create(properties);

        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        this.kafkaConsumer = new KafkaConsumer(properties);

        partitions = ((List<PartitionInfo>) this.kafkaConsumer.partitionsFor(topic))
                .stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition())).collect(Collectors.toList());
    }

    public long update() {
        logger.info("Updating offsets");
        logger.info("Getting consumer group offsets");
        try {
            kafkaAdminClient.listConsumerGroupOffsets(
                    groupId,
                    new ListConsumerGroupOffsetsOptions().topicPartitions(partitions)
            ).partitionsToOffsetAndMetadata()
                    .get()
                    .forEach(
                            (tp, meta) -> latestConsumerGroupOffsets.put(tp, meta.offset())
                    );
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Admin client failed:", e);
            return -1;
        }


        try {
            logger.info("Getting end offsets");
            ((Map<TopicPartition, Long>) kafkaConsumer.endOffsets(partitions))
                    .forEach(latestLogEndOffsets::put);
        } catch (Exception e) {
            logger.warn("Kafka consumer failed:", e);
            return -1;
        }

        return latestLogEndOffsets.entrySet()
                .stream()
                .map((entry) -> Math.max(0, entry.getValue()) - Math.max(0, latestConsumerGroupOffsets.get(entry.getKey())))
                .mapToLong(a -> a).sum();
    }

    public void stop() {
        kafkaAdminClient.close();
        kafkaConsumer.close();
    }

}
