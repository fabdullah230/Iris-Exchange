package com.iris.iris_matchingengine.model;

import com.iris.common.model.Order;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderBookEntry {
    private final String orderId;
    private final String clOrdId;
    private final String instrumentId;
    private final String side; // "BUY" or "SELL"
    private final double originalQuantity;
    private double remainingQuantity;
    private final double price;
    private final String orderType; // "LIMIT", "MARKET", etc.
    private final String timeInForce; // "DAY", "IOC", "FOK", etc.
    private final Instant entryTime;
    private final String clientId;
    private final String sourceIp;

    // For quick retrieval during matching
    private int sequenceNumber;

    public static OrderBookEntry fromOrder(Order order, String clientId, int sequenceNumber) {
        return OrderBookEntry.builder()
                .orderId(order.getOrderId() != null ? order.getOrderId() : UUID.randomUUID().toString())
                .clOrdId(order.getClOrdId())
                .instrumentId(order.getInstrumentId())
                .side(order.getSide())
                .originalQuantity(order.getQuantity())
                .remainingQuantity(order.getQuantity())
                .price(order.getPrice() != null ? order.getPrice() : 0)
                .orderType(order.getOrderType())
                .timeInForce(order.getTimeInForce())
                .entryTime(Instant.now())
                .clientId(clientId)
                .sourceIp(order.getSourceIpAddress())
                .sequenceNumber(sequenceNumber)
                .build();
    }

    public boolean isFilled() {
        return remainingQuantity <= 0;
    }
}