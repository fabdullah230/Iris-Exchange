package com.iris.common.model.messages;

import com.iris.common.model.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewOrderMessage {
    private String messageType;
    private String messageId;
    private long timestamp;
    private String clientId;
    private Order order;
}