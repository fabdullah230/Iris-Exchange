package com.iris.iris_matchingengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchResult {
    private final OrderBookEntry aggressorOrder; // The incoming order
    private final OrderBookEntry restingOrder;   // The resting order
    private final double matchedQuantity;
    private final double matchPrice;
    private final String tradeId;
}