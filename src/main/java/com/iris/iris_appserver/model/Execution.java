package com.iris.iris_appserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Execution {
    private String orderId;
    private String clOrdId;
    private String execId;
    private String instrumentId;
    private String side;
    private String execType;
    private String orderStatus;
    private double filledQuantity;
    private double remainingQuantity;
    private Double price;
    private Double lastPrice;
    private Double lastQuantity;
    private double avgPrice;
    private String text;
    private String tradeId;
    private String contraParty;
}