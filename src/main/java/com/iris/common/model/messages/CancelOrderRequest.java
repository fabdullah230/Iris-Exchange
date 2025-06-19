package com.iris.common.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderRequest {
    private String origOrderId;
    private String origClOrdId;
    private String clOrdId;
    private String instrumentId;
    private String side;
    private String sourceIpAddress;
}