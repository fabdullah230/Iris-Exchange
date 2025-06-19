package com.iris.common.model.exchange_operations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {
    private String symbol;
    private double lastSettlementPrice;
    private double volumeTickSize;
    private String tradingHours; // Format: "0000-2359"

    // Default trading hours
    public static final String DEFAULT_TRADING_HOURS = "0000-2359";

    // Helper method to check if trading is allowed at a specific time
    public boolean isTradingAllowed(String currentTime) {
        if (tradingHours == null || tradingHours.isEmpty() || DEFAULT_TRADING_HOURS.equals(tradingHours)) {
            return true; // 24/7 trading
        }

        // Parse trading hours and current time
        String[] hours = tradingHours.split("-");
        if (hours.length != 2) {
            return true; // Invalid format, default to allowing trading
        }

        try {
            int start = Integer.parseInt(hours[0]);
            int end = Integer.parseInt(hours[1]);
            int current = Integer.parseInt(currentTime.replace(":", ""));

            if (start <= end) {
                return current >= start && current <= end;
            } else {
                // Handles overnight sessions (e.g., 2200-0600)
                return current >= start || current <= end;
            }
        } catch (NumberFormatException e) {
            return true; // Error parsing, default to allowing trading
        }
    }
}