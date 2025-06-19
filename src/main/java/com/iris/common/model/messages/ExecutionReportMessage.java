package com.iris.common.model.messages;

import com.iris.common.model.Execution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionReportMessage {
    private String messageType;
    private String messageId;
    private long timestamp;
    private String clientId;
    private Execution execution;
}