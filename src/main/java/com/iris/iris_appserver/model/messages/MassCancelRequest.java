package com.iris.iris_appserver.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassCancelRequest {
    private String clOrdId;
    private String cancelType;
    private String instrumentId;
    private String side;
    private String sourceIpAddress;
}