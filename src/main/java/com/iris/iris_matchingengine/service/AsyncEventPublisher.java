package com.iris.iris_matchingengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.common.model.db.Order;
import com.iris.common.model.db.Trade;
import com.iris.common.model.db.OrderBookState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncEventPublisher {
    private final KafkaTemplate<String, byte[]> kafkaTemplate; // Using byte[] for serialized messages
    private final ObjectMapper objectMapper; // Used for JSON serialization

    private static final String ORDER_TOPIC = "iris.db.orders";
    private static final String TRADE_TOPIC = "iris.db.trades";
    private static final String ORDER_BOOK_STATE_TOPIC = "iris.db.orderbook";

    /**
     * Publishes an Order to the Kafka topic.
     *
     * @param order the Order object to publish
     */
    @Async("eventPublisherExecutor")
    public void publishOrder(Order order) {
        try {
            byte[] message = objectMapper.writeValueAsBytes(order);
            kafkaTemplate.send(ORDER_TOPIC, order.getOrderId(), message);
            log.debug("Published Order: {}", order.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish Order: {}", order.getOrderId(), e);
        }
    }

    /**
     * Publishes a Trade to the Kafka topic.
     *
     * @param trade the Trade object to publish
     */
    @Async("eventPublisherExecutor")
    public void publishTrade(Trade trade) {
        try {
            byte[] message = objectMapper.writeValueAsBytes(trade);
            kafkaTemplate.send(TRADE_TOPIC, trade.getTradeId(), message);
            log.debug("Published Trade: {}", trade.getTradeId());
        } catch (Exception e) {
            log.error("Failed to publish Trade: {}", trade.getTradeId(), e);
        }
    }

    /**
     * Publishes an OrderBookState to the Kafka topic.
     *
     * @param orderBookState the OrderBookState object to publish
     */
    @Async("eventPublisherExecutor")
    public void publishOrderBookState(OrderBookState orderBookState) {
        try {
            byte[] message = objectMapper.writeValueAsBytes(orderBookState);
            kafkaTemplate.send(ORDER_BOOK_STATE_TOPIC,
                    orderBookState.getInstrumentId(),
                    message);
            log.debug("Published OrderBookState for Instrument ID: {}",
                    orderBookState.getInstrumentId());
        } catch (Exception e) {
            log.error("Failed to publish OrderBookState for Instrument ID: {}",
                    orderBookState.getInstrumentId(), e);
        }
    }
}