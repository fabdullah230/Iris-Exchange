package com.iris.iris_matchingengine.model;

import com.iris.common.model.NewOrder;
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

    public static OrderBookEntry fromOrder(NewOrder newOrder, String clientId, int sequenceNumber) {
        return OrderBookEntry.builder()
                .orderId(newOrder.getOrderId() != null ? newOrder.getOrderId() : UUID.randomUUID().toString())
                .clOrdId(newOrder.getClOrdId())
                .instrumentId(newOrder.getInstrumentId())
                .side(newOrder.getSide())
                .originalQuantity(newOrder.getQuantity())
                .remainingQuantity(newOrder.getQuantity())
                .price(newOrder.getPrice() != null ? newOrder.getPrice() : 0)
                .orderType(newOrder.getOrderType())
                .timeInForce(newOrder.getTimeInForce())
                .entryTime(Instant.now())
                .clientId(clientId)
                .sourceIp(newOrder.getSourceIpAddress())
                .sequenceNumber(sequenceNumber)
                .build();
    }

    public boolean isFilled() {
        return remainingQuantity <= 0;
    }
}