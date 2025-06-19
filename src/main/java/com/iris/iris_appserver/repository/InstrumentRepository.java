package com.iris.iris_appserver.repository;

import com.iris.common.model.Instrument;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InstrumentRepository {

    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${exchange.operations.url:http://localhost:8082}")
    private String exchangeOperationsUrl;

    @PostConstruct
    public void initialize() {
        log.info("Initializing instrument repository from Exchange Operations API");
        try {
            // Fetch instruments from the Exchange Operations API
            String apiUrl = exchangeOperationsUrl + "/api/instruments";
            Map<String, Map<String, Object>> response = restTemplate.getForObject(
                    apiUrl, Map.class);

            if (response != null && !response.isEmpty()) {
                response.forEach((symbol, data) -> {
                    try {
                        double lastSettlementPrice = getDoubleValue(data.get("lastSettlementPrice"));
                        double volumeTickSize = getDoubleValue(data.get("volumeTickSize"));
                        String tradingHours = (String) data.get("tradingHours");

                        Instrument instrument = new Instrument(
                                symbol,
                                lastSettlementPrice,
                                volumeTickSize,
                                tradingHours,
                                true
                        );

                        instruments.put(symbol, instrument);
                        log.info("Loaded instrument from API: {} with settlement price {}",
                                symbol, lastSettlementPrice);
                    } catch (Exception e) {
                        log.error("Error processing instrument data for {}: {}", symbol, e.getMessage());
                    }
                });

                log.info("Successfully initialized instrument repository with {} instruments from API",
                        instruments.size());
            } else {
                log.warn("No instruments received from Exchange Operations API, falling back to sample data");
                initializeWithSampleData();
            }
        } catch (Exception e) {
            log.error("Failed to initialize instruments from API: {}", e.getMessage());
            log.info("Falling back to sample instrument data");
            initializeWithSampleData();
        }
    }

    private void initializeWithSampleData() {
        // Add some sample instruments as fallback
        addInstrument("AAPL", 150.25);
        addInstrument("MSFT", 285.75);
        addInstrument("GOOGL", 125.50);
        addInstrument("AMZN", 130.80);
        addInstrument("TSLA", 190.35);
        log.info("Initialized instrument repository with {} sample instruments", instruments.size());
    }

    // Helper to safely convert numeric values from JSON
    private double getDoubleValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    public void addInstrument(String symbol, double lastTradePrice) {
        instruments.put(symbol, new Instrument(symbol, lastTradePrice, true));
    }

    public Optional<Instrument> findBySymbol(String symbol) {
        return Optional.ofNullable(instruments.get(symbol));
    }

    /**
     * Updates the last trade price for an instrument
     * This is called by the InstrumentService when market data is received
     */
    public void updateLastTradePrice(String symbol, double price) {
        Instrument instrument = instruments.get(symbol);
        if (instrument != null) {
            instrument.setLastTradePrice(price);
            log.debug("Repository updated last trade price for {}: {}", symbol, price);
        } else {
            log.warn("Attempted to update price for unknown instrument: {}", symbol);
            // Consider adding the instrument dynamically if it doesn't exist
            addInstrument(symbol, price);
            log.info("Dynamically added new instrument: {} with price {}", symbol, price);
        }
    }

    public boolean isValidPriceRange(String symbol, double price) {
        Instrument instrument = instruments.get(symbol);
        if (instrument != null) {
            double lastPrice = instrument.getLastTradePrice();
            double lowerBound = lastPrice * 0.9; // 10% lower
            double upperBound = lastPrice * 1.1; // 10% higher
            return price >= lowerBound && price <= upperBound;
        }
        return false;
    }

    /**
     * Checks if trading is allowed for the instrument at the current time
     */
    public boolean isTradingAllowed(String symbol) {
        Instrument instrument = instruments.get(symbol);
        if (instrument != null && instrument.isActive()) {
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
            return instrument.isTradingAllowed(currentTime);
        }
        return false;
    }

    /**
     * Get all instruments in the repository
     */
    public Map<String, Instrument> getAllInstruments() {
        return new ConcurrentHashMap<>(instruments);
    }
}