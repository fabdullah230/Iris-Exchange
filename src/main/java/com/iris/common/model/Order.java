package com.iris.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String clOrdId;
    private String instrumentId;
    private String side;
    private double quantity;
    private String orderType;
    private Double price;
    private String timeInForce;
    private String sourceIpAddress;
    private ClientInfo clientInfo;
}