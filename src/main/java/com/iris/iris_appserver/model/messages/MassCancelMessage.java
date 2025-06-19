package com.iris.iris_appserver.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassCancelMessage {
    private String messageType;
    private String messageId;
    private long timestamp;
    private String clientId;
    private MassCancelRequest massCancel;
}