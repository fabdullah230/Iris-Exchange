package com.iris.iris_matchingengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.common.model.Execution;

import com.iris.common.model.NewOrder;
import com.iris.common.model.db.Order;
import com.iris.common.model.db.Trade;
import com.iris.common.model.messages.*;
import com.iris.iris_matchingengine.model.MatchResult;
import com.iris.iris_matchingengine.model.OrderBook;
import com.iris.iris_matchingengine.model.OrderBookEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {
    private final InstrumentService instrumentService;
    private final OrderBookManager orderBookManager;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final String executionTopic = "outbound-executions";
    private final ObjectMapper objectMapper;


    private final AsyncEventPublisher asyncEventPublisher;

    // Add these helper methods to create DB entities
    private Order createDbOrder(OrderBookEntry entry, String status) {
        return Order.builder()
                .orderId(entry.getOrderId())
                .clOrdId(entry.getClOrdId())
                .instrumentId(entry.getInstrumentId())
                .side(entry.getSide())
                .quantity(BigDecimal.valueOf(entry.getOriginalQuantity()))
                .remainingQuantity(BigDecimal.valueOf(entry.getRemainingQuantity()))
                .price(BigDecimal.valueOf(entry.getPrice()))
                .orderType(entry.getOrderType())
                .timeInForce(entry.getTimeInForce())
                .clientId(entry.getClientId())
                .sourceIp(entry.getSourceIp())
                .entryTime(LocalDateTime.now())
                .lastUpdatedTime(LocalDateTime.now())
                .status(status)
                .build();
    }

    private Trade createDbTrade(MatchResult match) {
        return Trade.builder()
                .tradeId(match.getTradeId())
                .instrumentId(match.getAggressorOrder().getInstrumentId())
                .price(BigDecimal.valueOf(match.getMatchPrice()))
                .quantity(BigDecimal.valueOf(match.getMatchedQuantity()))
                .buyerOrderId(match.getAggressorOrder().getSide().equals("BUY") ?
                        match.getAggressorOrder().getOrderId() :
                        match.getRestingOrder().getOrderId())
                .sellerOrderId(match.getAggressorOrder().getSide().equals("SELL") ?
                        match.getAggressorOrder().getOrderId() :
                        match.getRestingOrder().getOrderId())
                .buyerClOrdId(match.getAggressorOrder().getSide().equals("BUY") ?
                        match.getAggressorOrder().getClOrdId() :
                        match.getRestingOrder().getClOrdId())
                .sellerClOrdId(match.getAggressorOrder().getSide().equals("SELL") ?
                        match.getAggressorOrder().getClOrdId() :
                        match.getRestingOrder().getClOrdId())
                .buyerClientId(match.getAggressorOrder().getSide().equals("BUY") ?
                        match.getAggressorOrder().getClientId() :
                        match.getRestingOrder().getClientId())
                .sellerClientId(match.getAggressorOrder().getSide().equals("SELL") ?
                        match.getAggressorOrder().getClientId() :
                        match.getRestingOrder().getClientId())
                .tradeTime(LocalDateTime.now())
                .build();
    }



    /**
     * Process a new order
     * @param message New order message
     */
    public void processNewOrder(NewOrderMessage message) {
        NewOrder newOrder = message.getNewOrder();
        String clientId = message.getClientId();

        // Validate instrument
        if (!instrumentService.isValidInstrument(newOrder.getInstrumentId())) {
            sendRejection(newOrder, clientId, "Invalid instrument");
            return;
        }

        // Validate order type and other fields
        if (!isValidOrderType(newOrder)) {
            sendRejection(newOrder, clientId, "Invalid order type or parameters");
            return;
        }

        boolean isBuyOrder;
        if ("BUY".equalsIgnoreCase(newOrder.getSide())) {
            isBuyOrder = true;
        } else if ("SELL".equalsIgnoreCase(newOrder.getSide())) {
            isBuyOrder = false;
        } else {
            sendRejection(newOrder, clientId, "Invalid order side: " + newOrder.getSide());
            return;
        }

        // Create order book entry
        OrderBookEntry entry = OrderBookEntry.fromOrder(newOrder, clientId, 0);
        asyncEventPublisher.publishOrder(createDbOrder(entry, "NEW"));


        // Process order depending on side (buy or sell)
        OrderBook orderBook = orderBookManager.getOrderBook(newOrder.getInstrumentId());
        List<MatchResult> matches;

        if (isBuyOrder) {
            matches = orderBook.matchBuyOrder(entry);
        } else {
            matches = orderBook.matchSellOrder(entry);
        }

        // Process matches and send execution reports
        processMatches(matches, entry, clientId);

        // If order has remaining quantity and is not IOC, add to book
        if (entry.getRemainingQuantity() > 0 && !"IOC".equals(entry.getTimeInForce())) {
            orderBook.addOrder(entry);
            sendExecutionReport(entry, clientId, "NEW", null, null);
            asyncEventPublisher.publishOrder(createDbOrder(entry, "NEW"));
        } else if ("IOC".equals(entry.getTimeInForce()) && entry.getRemainingQuantity() > 0) {
            // For IOC orders, cancel any remaining quantity
            sendExecutionReport(entry, clientId, "CANCELED", "Immediate-or-cancel", null);
            asyncEventPublisher.publishOrder(createDbOrder(entry, "CANCELED"));
        }
    }

    /**
     * Process a cancel order request
     * @param message Cancel order message
     */
    public void processCancelOrder(CancelOrderMessage message) {
        CancelOrderRequest cancel = message.getCancel();
        String clientId = message.getClientId();

        // Get the order book for this instrument
        OrderBook orderBook = orderBookManager.getOrderBook(cancel.getInstrumentId());

        // Lookup the order by id or client id
        OrderBookEntry order = null;
        if (cancel.getOrigOrderId() != null) {
            order = orderBook.getOrderById(cancel.getOrigOrderId());
        } else if (cancel.getOrigClOrdId() != null) {
            order = orderBook.getOrderByClientOrderId(cancel.getOrigClOrdId());
        }

        if (order == null) {
            // Order not found, send rejection
            sendCancelReject(cancel, clientId, "Order not found");
            return;
        }

        // Cancel the order
        OrderBookEntry canceledOrder = orderBook.cancelOrder(order.getOrderId());

        // Send execution report
        if (canceledOrder != null) {
            sendExecutionReport(canceledOrder, clientId, "CANCELED", "Order canceled by user", cancel.getClOrdId());
        }
    }

    /**
     * Process a replace order request
     * @param message Replace order message
     */
    public void processReplaceOrder(ReplaceOrderMessage message) {
        ReplaceOrderRequest replace = message.getReplace();
        String clientId = message.getClientId();

        // Get the order book for this instrument
        OrderBook orderBook = orderBookManager.getOrderBook(replace.getInstrumentId());

        // Lookup the original order
        OrderBookEntry origOrder = null;
        if (replace.getOrigOrderId() != null) {
            origOrder = orderBook.getOrderById(replace.getOrigOrderId());
        } else if (replace.getOrigClOrdId() != null) {
            origOrder = orderBook.getOrderByClientOrderId(replace.getOrigClOrdId());
        }

        if (origOrder == null) {
            // Order not found, send rejection
            sendReplaceReject(replace, clientId, "Original order not found");
            return;
        }

        // Create new order with replace parameters
        OrderBookEntry newOrder = OrderBookEntry.builder()
                .orderId(replace.getNewOrderId() != null ? replace.getNewOrderId() : UUID.randomUUID().toString())
                .clOrdId(replace.getClOrdId())
                .instrumentId(replace.getInstrumentId())
                .side(replace.getSide())
                .originalQuantity(replace.getQuantity())
                .remainingQuantity(replace.getQuantity())
                .price(replace.getPrice())
                .orderType(replace.getOrderType())
                .timeInForce(origOrder.getTimeInForce()) // Typically doesn't change
                .entryTime(Instant.now())
                .clientId(clientId)
                .sourceIp(replace.getSourceIpAddress())
                .build();

        // Replace the order (cancel-replace)
        OrderBookEntry canceledOrder = orderBook.replaceOrder(origOrder.getOrderId(), newOrder);

        // Send execution reports
        if (canceledOrder != null) {
            // Send canceled for original order
            sendExecutionReport(canceledOrder, clientId, "REPLACED", "Order replaced", replace.getClOrdId());

            // Process the new order like a regular new order
            List<MatchResult> matches;

            if ("BUY".equalsIgnoreCase(newOrder.getSide())) {
                matches = orderBook.matchBuyOrder(newOrder);
            } else {
                matches = orderBook.matchSellOrder(newOrder);
            }

            // Process matches and send execution reports
            processMatches(matches, newOrder, clientId);

            // If order has remaining quantity, add to book (already done in replaceOrder)
            if (newOrder.getRemainingQuantity() > 0) {
                sendExecutionReport(newOrder, clientId, "NEW", "Replacement order", null);
            }
        }
    }

    /**
     * Process matches and send execution reports
     * @param matches List of matches
     * @param aggressorOrder Incoming order
     * @param clientId Client ID
     */
    private void processMatches(List<MatchResult> matches, OrderBookEntry aggressorOrder, String clientId) {
        if (matches.isEmpty()) {
            return;
        }

        double cumulativeQty = 0;
        double avgPrice = 0;

        // Process each match
        for (MatchResult match : matches) {
            cumulativeQty += match.getMatchedQuantity();
            avgPrice = ((avgPrice * (cumulativeQty - match.getMatchedQuantity())) +
                    (match.getMatchPrice() * match.getMatchedQuantity())) / cumulativeQty;

            asyncEventPublisher.publishTrade(createDbTrade(match));

            // Update orders in DB
            asyncEventPublisher.publishOrder(createDbOrder(aggressorOrder,
                    aggressorOrder.getRemainingQuantity() > 0 ? "PARTIALLY_FILLED" : "FILLED"));
            asyncEventPublisher.publishOrder(createDbOrder(match.getRestingOrder(),
                    match.getRestingOrder().getRemainingQuantity() > 0 ? "PARTIALLY_FILLED" : "FILLED"));


            // Send fill for the aggressor order
            sendExecutionReport(
                    aggressorOrder,
                    clientId,
                    aggressorOrder.getRemainingQuantity() > 0 ? "PARTIAL_FILL" : "FILL",
                    null,
                    null,
                    match.getMatchedQuantity(),
                    match.getMatchPrice(),
                    cumulativeQty,
                    avgPrice,
                    match.getTradeId(),
                    match.getRestingOrder().getClientId()
            );

            // Send fill for the resting order
            sendExecutionReport(
                    match.getRestingOrder(),
                    match.getRestingOrder().getClientId(),
                    match.getRestingOrder().getRemainingQuantity() > 0 ? "PARTIAL_FILL" : "FILL",
                    null,
                    null,
                    match.getMatchedQuantity(),
                    match.getMatchPrice(),
                    match.getRestingOrder().getOriginalQuantity() - match.getRestingOrder().getRemainingQuantity(),
                    match.getMatchPrice(), // Simplified for this example
                    match.getTradeId(),
                    clientId
            );
        }
    }

    /**
     * Send an execution report
     * @param order Order
     * @param clientId Client ID
     * @param execType Execution type
     * @param text Text
     * @param clOrdId Client order ID
     */
    private void sendExecutionReport(OrderBookEntry order, String clientId, String execType,
                                     String text, String clOrdId) {
        sendExecutionReport(order, clientId, execType, text, clOrdId, 0, 0, 0, 0, null, null);
    }

    /**
     * Send an execution report
     * @param order Order
     * @param clientId Client ID
     * @param execType Execution type
     * @param text Text
     * @param clOrdId Client order ID
     * @param lastQty Last executed quantity
     * @param lastPrice Last executed price
     * @param cumQty Cumulative executed quantity
     * @param avgPrice Average execution price
     * @param tradeId Trade ID
     * @param contraParty Contra party
     */
    private void sendExecutionReport(OrderBookEntry order, String clientId, String execType,
                                     String text, String clOrdId, double lastQty, double lastPrice,
                                     double cumQty, double avgPrice, String tradeId, String contraParty) {
        Execution execution = Execution.builder()
                .orderId(order.getOrderId())
                .clOrdId(clOrdId != null ? clOrdId : order.getClOrdId())
                .execId(UUID.randomUUID().toString())
                .instrumentId(order.getInstrumentId())
                .side(order.getSide())
                .execType(execType)
                .orderStatus(getOrderStatus(execType, order))
                .filledQuantity(cumQty > 0 ? cumQty : (order.getOriginalQuantity() - order.getRemainingQuantity()))
                .remainingQuantity(order.getRemainingQuantity())
                .price(order.getPrice())
                .lastPrice(lastPrice > 0 ? lastPrice : null)
                .lastQuantity(lastQty > 0 ? lastQty : null)
                .avgPrice(avgPrice > 0 ? avgPrice : 0)
                .text(text)
                .tradeId(tradeId)
                .contraParty(contraParty)
                .build();

        ExecutionReportMessage message = ExecutionReportMessage.builder()
                .messageType("EXECUTION_REPORT")
                .messageId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toEpochMilli())
                .clientId(clientId)
                .execution(execution)
                .build();

        // Send to Kafka
        try {
            kafkaTemplate.send(executionTopic, objectMapper.writeValueAsBytes(message));
            log.debug("Sent execution report: {}", message);
        } catch (Exception e) {
            log.error("Error sending execution report", e);
        }
    }

    /**
     * Send a cancel reject
     * @param cancel Cancel request
     * @param clientId Client ID
     * @param reason Rejection reason
     */
    private void sendCancelReject(CancelOrderRequest cancel, String clientId, String reason) {
        Execution execution = Execution.builder()
                .orderId(cancel.getOrigOrderId())
                .clOrdId(cancel.getClOrdId())
                .execId(UUID.randomUUID().toString())
                .instrumentId(cancel.getInstrumentId())
                .side(cancel.getSide())
                .execType("CANCELED_REJECTED")
                .orderStatus("REJECTED")
                .text(reason)
                .build();

        ExecutionReportMessage message = ExecutionReportMessage.builder()
                .messageType("EXECUTION_REPORT")
                .messageId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toEpochMilli())
                .clientId(clientId)
                .execution(execution)
                .build();

        // Send to Kafka
        try {
            kafkaTemplate.send(executionTopic, objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending cancel reject", e);
        }
    }

    /**
     * Send a replace reject
     * @param replace Replace request
     * @param clientId Client ID
     * @param reason Rejection reason
     */
    private void sendReplaceReject(ReplaceOrderRequest replace, String clientId, String reason) {
        Execution execution = Execution.builder()
                .orderId(replace.getOrigOrderId())
                .clOrdId(replace.getClOrdId())
                .execId(UUID.randomUUID().toString())
                .instrumentId(replace.getInstrumentId())
                .side(replace.getSide())
                .execType("REPLACE_REJECTED")
                .orderStatus("REJECTED")
                .text(reason)
                .build();

        ExecutionReportMessage message = ExecutionReportMessage.builder()
                .messageType("EXECUTION_REPORT")
                .messageId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toEpochMilli())
                .clientId(clientId)
                .execution(execution)
                .build();

        // Send to Kafka
        try {
            kafkaTemplate.send(executionTopic, objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending replace reject", e);
        }
    }

    /**
     * Send a rejection for an invalid order
     * @param newOrder Order
     * @param clientId Client ID
     * @param reason Rejection reason
     */
    private void sendRejection(NewOrder newOrder, String clientId, String reason) {
        Execution execution = Execution.builder()
                .orderId(newOrder.getOrderId())
                .clOrdId(newOrder.getClOrdId())
                .execId(UUID.randomUUID().toString())
                .instrumentId(newOrder.getInstrumentId())
                .side(newOrder.getSide())
                .execType("REJECTED")
                .orderStatus("REJECTED")
                .text(reason)
                .build();

        ExecutionReportMessage message = ExecutionReportMessage.builder()
                .messageType("EXECUTION_REPORT")
                .messageId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toEpochMilli())
                .clientId(clientId)
                .execution(execution)
                .build();

        // Send to Kafka
        try {
            kafkaTemplate.send(executionTopic, objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending rejection", e);
        }
    }

    /**
     * Validate order type and parameters
     * @param newOrder Order
     * @return True if valid
     */
    private boolean isValidOrderType(NewOrder newOrder) {
        if (newOrder.getOrderType() == null) {
            return false;
        }

        // Basic validation
        if ("LIMIT".equalsIgnoreCase(newOrder.getOrderType())) {
            return newOrder.getPrice() != null && newOrder.getPrice() > 0;
        } else if ("MARKET".equalsIgnoreCase(newOrder.getOrderType())) {
            return true; // Market orders don't need price
        }

        return false;
    }

    /**
     * Get order status from execution type
     * @param execType Execution type
     * @param order Order
     * @return Order status
     */
    private String getOrderStatus(String execType, OrderBookEntry order) {
        switch (execType) {
            case "NEW":
                return "NEW";
            case "REJECTED":
                return "REJECTED";
            case "CANCELED":
                return "CANCELED";
            case "REPLACED":
                return "REPLACED";
            case "PARTIAL_FILL":
                return "PARTIALLY_FILLED";
            case "FILL":
                return "FILLED";
            default:
                return "UNKNOWN";
        }
    }
}