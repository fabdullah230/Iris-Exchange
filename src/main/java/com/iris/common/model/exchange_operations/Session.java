package com.iris.common.model.exchange_operations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private String name;
    private String fixSenderCompId;
    private String clearingAccount;
}