package com.iris.common.model.messages;

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