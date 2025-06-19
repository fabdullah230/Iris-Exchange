package com.iris.iris_appserver.service;

import com.iris.common.model.Instrument;
import com.iris.iris_appserver.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;
    private final Map<String, Double> lastTradePrices = new ConcurrentHashMap<>();

    /**
     * Updates the instrument price based on market data from the matching engine
     */
    public void updateInstrumentPrice(String symbol, double price) {
        // Update our cache of latest prices
        lastTradePrices.put(symbol, price);

        // Update the instrument in the repository
        instrumentRepository.updateLastTradePrice(symbol, price);

        log.debug("Updated last trade price for {}: {}", symbol, price);
    }

    /**
     * Gets the last trade price for an instrument
     */
    public Optional<Double> getLastTradePrice(String symbol) {
        return Optional.ofNullable(lastTradePrices.get(symbol));
    }

    /**
     * Validates whether the instrument exists
     */
    public boolean isValidInstrument(String symbol) {
        return instrumentRepository.findBySymbol(symbol).isPresent();
    }

    /**
     * Validates whether the price is within acceptable range based on last trade price
     */
    public boolean isValidPrice(String symbol, double price) {
        return instrumentRepository.isValidPriceRange(symbol, price);
    }

    /**
     * Gets full instrument details
     */
    public Optional<Instrument> getInstrument(String symbol) {
        return instrumentRepository.findBySymbol(symbol);
    }
}