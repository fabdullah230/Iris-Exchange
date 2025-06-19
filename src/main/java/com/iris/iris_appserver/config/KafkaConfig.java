package com.iris.iris_appserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Legacy Kafka configuration
 * Disabled in favor of the common ConnectionManager
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.use-legacy-kafka-config", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    // This class is now disabled by default.
    // All Kafka configuration now comes from com.iris.common.kafka.ConnectionManager

}