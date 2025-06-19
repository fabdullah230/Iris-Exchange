package com.iris.iris_matchingengine.model;

import com.iris.common.model.Execution;
import com.iris.common.model.messages.ExecutionReportMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class OrderBook {
    private final String instrumentId;
    private final String matchingAlgorithm;

    // Sequence counter for price-time priority
    private final AtomicInteger sequence = new AtomicInteger(0);

    // Maps for quick lookup by orderId and clOrdId
    private final Map<String, OrderBookEntry> orderIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> clOrdIdToOrderIdMap = new ConcurrentHashMap<>();

    // Buy orders are sorted in descending order by price (highest price first)
    // For same price, sorted by sequence number (lowest first) for time priority
    @Getter
    private final NavigableMap<Double, SortedMap<Integer, OrderBookEntry>> buyOrders =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());

    // Sell orders are sorted in ascending order by price (lowest price first)
    // For same price, sorted by sequence number (lowest first) for time priority
    @Getter
    private final NavigableMap<Double, SortedMap<Integer, OrderBookEntry>> sellOrders =
            new ConcurrentSkipListMap<>();

    public OrderBook(String instrumentId, String matchingAlgorithm) {
        this.instrumentId = instrumentId;
        this.matchingAlgorithm = matchingAlgorithm;
    }

    /**
     * Add an order to the book
     * @param entry Order to add
     */
    public void addOrder(OrderBookEntry entry) {
        // Assign sequence number for time priority
        int seqNum = sequence.incrementAndGet();
        entry.setSequenceNumber(seqNum);

        // Store in lookup maps
        orderIdMap.put(entry.getOrderId(), entry);
        if (entry.getClOrdId() != null) {
            clOrdIdToOrderIdMap.put(entry.getClOrdId(), entry.getOrderId());
        }

        // Add to appropriate price level
        if ("BUY".equalsIgnoreCase(entry.getSide())) {
            buyOrders.computeIfAbsent(entry.getPrice(), k -> new TreeMap<>())
                    .put(seqNum, entry);
        } else if ("SELL".equalsIgnoreCase(entry.getSide())) {
            sellOrders.computeIfAbsent(entry.getPrice(), k -> new TreeMap<>())
                    .put(seqNum, entry);
        }

        // Log the orderbook state
        logOrderBookState();
    }

    /**
     * Cancel an order from the book
     * @param orderId Order ID to cancel
     * @return The canceled order or null if not found
     */
    public OrderBookEntry cancelOrder(String orderId) {
        OrderBookEntry order = orderIdMap.get(orderId);
        if (order == null) {
            return null;
        }

        // Remove from lookup maps
        orderIdMap.remove(orderId);
        if (order.getClOrdId() != null) {
            clOrdIdToOrderIdMap.remove(order.getClOrdId());
        }

        // Remove from price levels
        if ("BUY".equalsIgnoreCase(order.getSide())) {
            SortedMap<Integer, OrderBookEntry> priceLevel = buyOrders.get(order.getPrice());
            if (priceLevel != null) {
                priceLevel.remove(order.getSequenceNumber());
                if (priceLevel.isEmpty()) {
                    buyOrders.remove(order.getPrice());
                }
            }
        } else if ("SELL".equalsIgnoreCase(order.getSide())) {
            SortedMap<Integer, OrderBookEntry> priceLevel = sellOrders.get(order.getPrice());
            if (priceLevel != null) {
                priceLevel.remove(order.getSequenceNumber());
                if (priceLevel.isEmpty()) {
                    sellOrders.remove(order.getPrice());
                }
            }
        }

        // Log the orderbook state
        logOrderBookState();

        return order;
    }

    /**
     * Replace an order (cancel-replace)
     * @param origOrderId Original order ID
     * @param newEntry New order entry
     * @return The old order or null if not found
     */
    public OrderBookEntry replaceOrder(String origOrderId, OrderBookEntry newEntry) {
        // First cancel the original order
        OrderBookEntry oldOrder = cancelOrder(origOrderId);
        if (oldOrder == null) {
            return null;
        }

        // Then add the new order with a new sequence number (loses time priority)
        addOrder(newEntry);

        return oldOrder;
    }

    /**
     * Get an order by its ID
     * @param orderId Order ID
     * @return The order or null if not found
     */
    public OrderBookEntry getOrderById(String orderId) {
        return orderIdMap.get(orderId);
    }

    /**
     * Get an order by its client order ID
     * @param clOrdId Client order ID
     * @return The order or null if not found
     */
    public OrderBookEntry getOrderByClientOrderId(String clOrdId) {
        String orderId = clOrdIdToOrderIdMap.get(clOrdId);
        if (orderId == null) {
            return null;
        }
        return orderIdMap.get(orderId);
    }

    /**
     * Match an incoming buy order against the sell side of the book
     * @param incomingOrder Order to match
     * @return List of executions created
     */
    public List<MatchResult> matchBuyOrder(OrderBookEntry incomingOrder) {
        List<MatchResult> executions = new ArrayList<>();
        double remainingQty = incomingOrder.getRemainingQuantity();

        // Process until we've filled the order or run out of matching sell orders
        while (remainingQty > 0 && !sellOrders.isEmpty()) {
            // Get the best (lowest) sell price
            Map.Entry<Double, SortedMap<Integer, OrderBookEntry>> bestSellLevel = sellOrders.firstEntry();

            // If best sell is higher than buy price, no match is possible
            if (bestSellLevel == null || bestSellLevel.getKey() > incomingOrder.getPrice()) {
                break;
            }

            // Get all orders at the best price level, ordered by time priority
            SortedMap<Integer, OrderBookEntry> ordersAtBestPrice = bestSellLevel.getValue();

            // Match against each resting order in time priority
            Iterator<Map.Entry<Integer, OrderBookEntry>> it = ordersAtBestPrice.entrySet().iterator();
            while (it.hasNext() && remainingQty > 0) {
                Map.Entry<Integer, OrderBookEntry> entry = it.next();
                OrderBookEntry restingOrder = entry.getValue();

                // Calculate trade quantity
                double tradeQty = Math.min(remainingQty, restingOrder.getRemainingQuantity());

                // Create match result
                MatchResult match = new MatchResult(
                        incomingOrder,
                        restingOrder,
                        tradeQty,
                        bestSellLevel.getKey(),
                        UUID.randomUUID().toString()  // Trade ID
                );
                executions.add(match);

                // Update remaining quantities
                remainingQty -= tradeQty;
                restingOrder.setRemainingQuantity(restingOrder.getRemainingQuantity() - tradeQty);

                // Remove completely filled resting orders
                if (restingOrder.isFilled()) {
                    it.remove();
                    orderIdMap.remove(restingOrder.getOrderId());
                    if (restingOrder.getClOrdId() != null) {
                        clOrdIdToOrderIdMap.remove(restingOrder.getClOrdId());
                    }
                }
            }

            // Remove price level if empty
            if (ordersAtBestPrice.isEmpty()) {
                sellOrders.remove(bestSellLevel.getKey());
            }
        }

        // Update the incoming order's remaining quantity
        incomingOrder.setRemainingQuantity(remainingQty);

        // Log the orderbook state
        logOrderBookState();

        return executions;
    }

    /**
     * Match an incoming sell order against the buy side of the book
     * @param incomingOrder Order to match
     * @return List of executions created
     */
    public List<MatchResult> matchSellOrder(OrderBookEntry incomingOrder) {
        List<MatchResult> executions = new ArrayList<>();
        double remainingQty = incomingOrder.getRemainingQuantity();

        // Process until we've filled the order or run out of matching buy orders
        while (remainingQty > 0 && !buyOrders.isEmpty()) {
            // Get the best (highest) buy price
            Map.Entry<Double, SortedMap<Integer, OrderBookEntry>> bestBuyLevel = buyOrders.firstEntry();

            // If best buy is lower than sell price, no match is possible
            if (bestBuyLevel == null || bestBuyLevel.getKey() < incomingOrder.getPrice()) {
                break;
            }

            // Get all orders at the best price level, ordered by time priority
            SortedMap<Integer, OrderBookEntry> ordersAtBestPrice = bestBuyLevel.getValue();

            // Match against each resting order in time priority
            Iterator<Map.Entry<Integer, OrderBookEntry>> it = ordersAtBestPrice.entrySet().iterator();
            while (it.hasNext() && remainingQty > 0) {
                Map.Entry<Integer, OrderBookEntry> entry = it.next();
                OrderBookEntry restingOrder = entry.getValue();

                // Calculate trade quantity
                double tradeQty = Math.min(remainingQty, restingOrder.getRemainingQuantity());

                // Create match result
                MatchResult match = new MatchResult(
                        incomingOrder,
                        restingOrder,
                        tradeQty,
                        bestBuyLevel.getKey(),
                        UUID.randomUUID().toString()  // Trade ID
                );
                executions.add(match);

                // Update remaining quantities
                remainingQty -= tradeQty;
                restingOrder.setRemainingQuantity(restingOrder.getRemainingQuantity() - tradeQty);

                // Remove completely filled resting orders
                if (restingOrder.isFilled()) {
                    it.remove();
                    orderIdMap.remove(restingOrder.getOrderId());
                    if (restingOrder.getClOrdId() != null) {
                        clOrdIdToOrderIdMap.remove(restingOrder.getClOrdId());
                    }
                }
            }

            // Remove price level if empty
            if (ordersAtBestPrice.isEmpty()) {
                buyOrders.remove(bestBuyLevel.getKey());
            }
        }

        // Update the incoming order's remaining quantity
        incomingOrder.setRemainingQuantity(remainingQty);

        // Log the orderbook state
        logOrderBookState();

        return executions;
    }

    /**
     * Log the current state of the order book in a readable format
     */
    public void logOrderBookState() {
        StringBuilder sb = new StringBuilder("\nOrderBook for " + instrumentId + ":\n");
        sb.append(String.format("%-15s | %-15s | %-15s | %-15s\n", "SELL QTY", "SELL PRICE", "BUY PRICE", "BUY QTY"));
        sb.append("----------------------------------------------------------------\n");

        // Combine all price levels for display
        NavigableSet<Double> allPrices = new TreeSet<>(Collections.reverseOrder());
        allPrices.addAll(buyOrders.keySet());
        allPrices.addAll(sellOrders.keySet());

        for (Double price : allPrices) {
            double sellQty = 0;
            if (sellOrders.containsKey(price)) {
                sellQty = sellOrders.get(price).values().stream()
                        .mapToDouble(OrderBookEntry::getRemainingQuantity)
                        .sum();
            }

            double buyQty = 0;
            if (buyOrders.containsKey(price)) {
                buyQty = buyOrders.get(price).values().stream()
                        .mapToDouble(OrderBookEntry::getRemainingQuantity)
                        .sum();
            }

            sb.append(String.format("%-15.2f | %-15.2f | %-15.2f | %-15.2f\n",
                    sellQty > 0 ? sellQty : 0,
                    sellQty > 0 ? price : 0,
                    buyQty > 0 ? price : 0,
                    buyQty > 0 ? buyQty : 0));
        }

        log.info(sb.toString());
    }
}