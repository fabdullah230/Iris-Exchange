package com.iris.iris_appserver.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.iris.iris_appserver.model.Order;


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