package com.iris.iris_matchingengine.service;

import com.iris.common.model.Instrument;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InstrumentService {
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${exchange.operations.url:http://localhost:8082}")
    private String exchangeOperationsUrl;

    @PostConstruct
    public void init() {
        log.info("Initializing instrument service from Exchange Operations API");
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

                        Instrument instrument = Instrument.builder()
                                .symbol(symbol)
                                .lastTradePrice(lastSettlementPrice) // Initialize with settlement price
                                .lastSettlementPrice(lastSettlementPrice)
                                .volumeTickSize(volumeTickSize)
                                .tradingHours(tradingHours)
                                .active(true)
                                .build();

                        instruments.put(symbol, instrument);
                        log.info("Loaded instrument from API: {} with settlement price {}",
                                symbol, lastSettlementPrice);
                    } catch (Exception e) {
                        log.error("Error processing instrument data for {}: {}", symbol, e.getMessage());
                    }
                });

                log.info("Successfully initialized instrument service with {} instruments from API",
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
        addInstrument("AAPL", "Apple Inc.", 170.50);
        addInstrument("MSFT", "Microsoft Corp.", 400.20);
        addInstrument("GOOGL", "Alphabet Inc.", 160.75);
        addInstrument("AMZN", "Amazon.com Inc.", 180.30);
        addInstrument("TSLA", "Tesla Inc.", 175.60);
        log.info("Initialized instrument service with {} sample instruments", instruments.size());
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

    private void addInstrument(String symbol, String name, double lastPrice) {
        instruments.put(symbol, new Instrument(symbol, lastPrice, true));
        // We don't use name here anymore since it's not in the API response
    }

    public boolean isValidInstrument(String instrumentId) {
        Instrument instrument = instruments.get(instrumentId);
        if (instrument != null && instrument.isActive()) {
            // Also check if trading is allowed at the current time
            return isTradingAllowed(instrumentId);
        }
        return false;
    }

    public Instrument getInstrument(String instrumentId) {
        return instruments.get(instrumentId);
    }

    /**
     * Checks if trading is allowed for the instrument at the current time
     */
    public boolean isTradingAllowed(String instrumentId) {
        Instrument instrument = instruments.get(instrumentId);
        if (instrument != null && instrument.isActive()) {
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
            return instrument.isTradingAllowed(currentTime);
        }
        return false;
    }

    /**
     * Updates the last trade price for an instrument
     */
    public void updateLastTradePrice(String instrumentId, double price) {
        Instrument instrument = instruments.get(instrumentId);
        if (instrument != null) {
            instrument.setLastTradePrice(price);
            log.debug("Updated last trade price for {}: {}", instrumentId, price);
        }
    }

    /**
     * Get all instruments
     */
    public Map<String, Instrument> getAllInstruments() {
        return Collections.unmodifiableMap(instruments);
    }

    /**
     * Get the tick size for an instrument
     */
    public double getVolumeTickSize(String instrumentId) {
        Instrument instrument = instruments.get(instrumentId);
        return instrument != null ? instrument.getVolumeTickSize() : 1.0; // Default to 1.0 if not found
    }

    /**
     * Check if a price change is within valid range (optional functionality)
     */
    public boolean isValidPriceRange(String instrumentId, double price) {
        Instrument instrument = instruments.get(instrumentId);
        if (instrument != null) {
            double lastPrice = instrument.getLastTradePrice();
            double lowerBound = lastPrice * 0.9; // 10% lower
            double upperBound = lastPrice * 1.1; // 10% higher
            return price >= lowerBound && price <= upperBound;
        }
        return false;
    }
}