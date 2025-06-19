package com.iris.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class KafkaModeConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.mode", havingValue = "appserver")
    public AppServerKafkaConfig appServerConfig() {
        log.info("Loading AppServer Kafka configuration");
        return new AppServerKafkaConfig();
    }

    @Bean
    @ConditionalOnProperty(name = "app.mode", havingValue = "matchingengine")
    public MatchingEngineKafkaConfig matchingEngineConfig() {
        log.info("Loading Matching Engine Kafka configuration");
        return new MatchingEngineKafkaConfig();
    }

    // Configuration specific to App Server
    public static class AppServerKafkaConfig {
        // Any app-server specific configurations can go here
    }

    // Configuration specific to Matching Engine
    public static class MatchingEngineKafkaConfig {
        // Any matching-engine specific configurations can go here
    }
}