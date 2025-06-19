package com.iris.common.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaTopics {

    private final Map<String, String> topics;

    @Autowired
    public KafkaTopics(@Qualifier("kafkaTopicMap") Map<String, String> kafkaTopics) {
        this.topics = kafkaTopics;
    }

    public String getInboundOrdersTopic() {
        return topics.get("inbound-orders");
    }

    public String getOutboundExecutionsTopic() {
        return topics.get("outbound-executions");
    }

    public String getMarketDataTopic() {
        return topics.get("market-data");
    }

    public String getInstrumentUpdatesTopic() {
        return topics.get("instrument-updates");
    }

    public String getSystemControlTopic() {
        return topics.get("system-control");
    }
}