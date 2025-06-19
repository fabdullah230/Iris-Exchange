package com.iris.iris_matchingengine.service;

import com.iris.iris_matchingengine.model.OrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderBookManager {
    // Default matching algorithm
    private static final String DEFAULT_ALGORITHM = "PRICE_TIME_PRIORITY";

    // Map of instrument ID to order book
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    /**
     * Get or create an order book for an instrument
     * @param instrumentId Instrument ID
     * @return The order book
     */
    public OrderBook getOrderBook(String instrumentId) {
        return orderBooks.computeIfAbsent(instrumentId,
                id -> createOrderBook(id, DEFAULT_ALGORITHM));
    }

    /**
     * Create a new order book with the specified algorithm
     * @param instrumentId Instrument ID
     * @param algorithm Matching algorithm
     * @return The new order book
     */
    private OrderBook createOrderBook(String instrumentId, String algorithm) {
        log.info("Creating new order book for instrument: {} with algorithm: {}",
                instrumentId, algorithm);

        return new OrderBook(instrumentId, algorithm);
    }
}