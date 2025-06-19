package com.iris.common.kafka;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@Configuration
@EnableScheduling
public class ConnectionManager {

    @Getter
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.mode:appserver}")
    private String appMode;

    @Value("${app.kafka.create-topics:true}")
    private boolean createTopics;

    @Value("${app.kafka.topic.inbound-orders:inbound-orders}")
    private String inboundOrdersTopic;

    @Value("${app.kafka.topic.outbound-executions:outbound-executions}")
    private String outboundExecutionsTopic;

    @Value("${app.kafka.topic.market-data:market-data}")
    private String marketDataTopic;

    @Value("${app.kafka.topic.instrument-updates:instrument-updates}")
    private String instrumentUpdatesTopic;

    @Value("${app.kafka.topic.system-control:system-control}")
    private String systemControlTopic;

    private final Map<String, Integer> topicPartitions = new HashMap<>();
    private final Map<String, Short> topicReplications = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing Kafka ConnectionManager in {} mode", appMode);
        log.info("Application name: {}", applicationName);
        log.info("Bootstrap servers: {}", bootstrapServers);

        // Log all configured topic names
        log.info("Configured topics:");
        log.info("  inbound-orders: {}", inboundOrdersTopic);
        log.info("  outbound-executions: {}", outboundExecutionsTopic);
        log.info("  market-data: {}", marketDataTopic);
        log.info("  instrument-updates: {}", instrumentUpdatesTopic);
        log.info("  system-control: {}", systemControlTopic);

        // Configure topic settings
        topicPartitions.put(inboundOrdersTopic, 3);
        topicPartitions.put(outboundExecutionsTopic, 3);
        topicPartitions.put(marketDataTopic, 3);
        topicPartitions.put(instrumentUpdatesTopic, 1);
        topicPartitions.put(systemControlTopic, 1);

        for (String topic : topicPartitions.keySet()) {
            topicReplications.put(topic, (short) 1); // Use 3 for production
        }

        if (createTopics) {
            createKafkaTopics();
        }

        // Log connection status
        log.info("Kafka ConnectionManager initialized with bootstrap servers: {}", bootstrapServers);
        log.info("Application will {} create topics if they don't exist", createTopics ? "" : "not ");
    }

    private void createKafkaTopics() {
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers
        ))) {
            // Get existing topics
            Set<String> existingTopics = admin.listTopics().names().get();
            log.info("Existing Kafka topics: {}", existingTopics);

            // Create missing topics
            List<NewTopic> topicsToCreate = new ArrayList<>();

            for (String topic : topicPartitions.keySet()) {
                if (!existingTopics.contains(topic)) {
                    topicsToCreate.add(new NewTopic(
                            topic,
                            topicPartitions.get(topic),
                            topicReplications.get(topic)
                    ));
                    log.info("Will create topic: {} with {} partitions",
                            topic, topicPartitions.get(topic));
                } else {
                    log.info("Topic already exists: {}", topic);
                }
            }

            if (!topicsToCreate.isEmpty()) {
                // Create topics one by one to identify which one might be failing
                for (NewTopic topic : topicsToCreate) {
                    try {
                        log.info("Creating topic: {}", topic.name());
                        CreateTopicsResult result = admin.createTopics(Collections.singleton(topic));
                        result.all().get();
                        log.info("Successfully created topic: {}", topic.name());
                    } catch (Exception e) {
                        log.error("Failed to create topic {}: {}", topic.name(), e.getMessage(), e);
                        // Continue with other topics
                    }
                }
            } else {
                log.info("No new topics to create");
            }

            // Verify all topics exist after creation attempts
            try {
                Set<String> finalTopics = admin.listTopics().names().get();
                for (String expectedTopic : topicPartitions.keySet()) {
                    if (finalTopics.contains(expectedTopic)) {
                        log.info("Verified topic exists: {}", expectedTopic);
                    } else {
                        log.warn("Topic still does not exist after creation attempt: {}", expectedTopic);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to verify topics", e);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create Kafka topics", e);
            // Don't fail startup, but log the error
        }
    }

    @Bean(name = "commonClientId")
    public String clientId() {
        return applicationName;
    }

    @Bean
    public ProducerFactory<String, byte[]> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, applicationName);
        // Enable idempotence for exactly-once semantics
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        // Performance tuning
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5); // milliseconds to wait before sending
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // Start reading from beginning if no offset found
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // Disable auto commit to ensure we process messages before committing
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Performance tuning
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public Map<String, String> kafkaTopicMap() {  // renamed from kafkaTopics
        Map<String, String> topics = new HashMap<>();
        topics.put("inbound-orders", inboundOrdersTopic);
        topics.put("outbound-executions", outboundExecutionsTopic);
        topics.put("market-data", marketDataTopic);
        topics.put("instrument-updates", instrumentUpdatesTopic);
        topics.put("system-control", systemControlTopic);
        return topics;
    }

}