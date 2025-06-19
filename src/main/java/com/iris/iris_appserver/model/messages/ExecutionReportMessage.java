package com.iris.iris_appserver.model.messages;

import com.iris.iris_appserver.model.Execution;
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