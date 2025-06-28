package com.iris.iris_dbwriter;

import com.iris.common.model.db.*;
import com.iris.iris_dbwriter.repository.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.Acknowledgment;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseWriterService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final OrderBookStateRepository orderBookStateRepository;
//    private final FixMessageRepository fixMessageRepository;

    @KafkaListener(topics = "${app.kafka.topic.db-orders}", groupId = "${spring.application.name}")
    @Transactional
    public void consumeOrders(Order order, Acknowledgment ack) {
        try {
            orderRepository.save(order);
            log.info("Order saved successfully: {}", order.getOrderId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error saving order: {}", e.getMessage(), e);
            // Don't acknowledge - message will be redelivered
        }
    }

    @KafkaListener(topics = "${app.kafka.topic.db-trades}", groupId = "${spring.application.name}")
    @Transactional
    public void consumeTrades(Trade trade, Acknowledgment ack) {
        try {
            tradeRepository.save(trade);
            log.info("Trade saved successfully: {}", trade.getTradeId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error saving trade: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topic.db-orderbook}", groupId = "${spring.application.name}")
    @Transactional
    public void consumeOrderBookState(OrderBookState orderBookState, Acknowledgment ack) {
        try {
            orderBookStateRepository.save(orderBookState);
            log.info("OrderBook state saved successfully for instrument: {}",
                    orderBookState.getInstrumentId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error saving orderbook state: {}", e.getMessage(), e);
        }
    }

//    @KafkaListener(topics = "${app.kafka.topic.db-fixmessages}", groupId = "${spring.application.name}")
//    @Transactional
//    public void consumeFixMessages(FixMessage fixMessage, Acknowledgment ack) {
//        try {
//            fixMessageRepository.save(fixMessage);
//            log.info("FIX message saved successfully: {} -> {}",
//                    fixMessage.getSenderCompId(),
//                    fixMessage.getTargetCompId());
//            ack.acknowledge();
//        } catch (Exception e) {
//            log.error("Error saving FIX message: {}", e.getMessage(), e);
//        }
//    }
}