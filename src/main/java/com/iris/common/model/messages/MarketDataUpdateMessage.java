package com.iris.common.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataUpdateMessage {
    private String messageType;
    private String messageId;
    private long timestamp;
    private String instrumentId;
    private double lastTradePrice;
    private double lastTradeQuantity;
    private long lastTradeTime;
    private String tradingStatus;
}