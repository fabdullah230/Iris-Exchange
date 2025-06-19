package com.iris.common.model;

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
    private double lastTradePrice;
    private double lastSettlementPrice;
    private double volumeTickSize;
    private String tradingHours;
    private boolean active;

    // Constructor to maintain backward compatibility
    public Instrument(String symbol, double lastTradePrice, boolean active) {
        this.symbol = symbol;
        this.lastTradePrice = lastTradePrice;
        this.lastSettlementPrice = lastTradePrice;
        this.volumeTickSize = 1.0;
        this.tradingHours = "0000-2359";
        this.active = active;
    }

    // Constructor for creating from exchange operations data
    public Instrument(String symbol, double lastSettlementPrice, double volumeTickSize,
                      String tradingHours, boolean active) {
        this.symbol = symbol;
        this.lastTradePrice = lastSettlementPrice; // Initialize trade price with settlement price
        this.lastSettlementPrice = lastSettlementPrice;
        this.volumeTickSize = volumeTickSize;
        this.tradingHours = tradingHours;
        this.active = active;
    }

    // Helper method to check if trading is allowed at a specific time
    public boolean isTradingAllowed(String currentTime) {
        if (tradingHours == null || tradingHours.isEmpty() || "0000-2359".equals(tradingHours)) {
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