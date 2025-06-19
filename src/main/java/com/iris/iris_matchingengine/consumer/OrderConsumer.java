package com.iris.iris_matchingengine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.common.model.messages.CancelOrderMessage;
import com.iris.common.model.messages.NewOrderMessage;
import com.iris.common.model.messages.ReplaceOrderMessage;
import com.iris.iris_matchingengine.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConsumer {
    private final ObjectMapper objectMapper;
    private final OrderProcessingService orderProcessingService;

    /**
     * Listens for new orders on the inbound-orders topic
     * Routes them to the appropriate orderbook for matching
     *
     * @param message Raw message bytes from Kafka
     * @param acknowledgment Kafka acknowledgment object
     */
    @KafkaListener(topics = "${app.kafka.topic.inbound-orders:inbound-orders}",
            groupId = "${spring.application.name}")
    public void consumeNewOrders(byte[] message, Acknowledgment acknowledgment) {
        try {

                NewOrderMessage orderMessage = objectMapper.readValue(message, NewOrderMessage.class);
                log.debug("Received new order: {}", orderMessage);

                orderProcessingService.processNewOrder(orderMessage);


            // Acknowledge message processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order message", e);
            // In production, consider a dead-letter queue for failed messages
            acknowledgment.acknowledge();
        }
    }
}