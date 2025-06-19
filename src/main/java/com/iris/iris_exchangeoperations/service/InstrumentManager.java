package com.iris.iris_exchangeoperations.service;

import com.iris.common.model.exchange_operations.Instrument;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InstrumentManager {

    @Value("${exchange.instruments.file:instruments.csv}")
    private String instrumentsFilePath;
    private final ResourceLoader resourceLoader;

    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    public InstrumentManager(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }


    @PostConstruct
    public void loadInstruments() {
        log.info("Loading instruments from: {}", instrumentsFilePath);
        Path path = Paths.get(instrumentsFilePath);

        Resource resource = resourceLoader.getResource("classpath:" + instrumentsFilePath);

        if (!resource.exists()) {
            log.warn("Instruments file not found: {}", instrumentsFilePath);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip empty lines or comments
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String symbol = parts[0].trim();
                    double price = Double.parseDouble(parts[1].trim());

                    // Default values if not provided
                    double tickSize = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 1.0;
                    String hours = parts.length > 3 ? parts[3].trim() : Instrument.DEFAULT_TRADING_HOURS;

                    Instrument instrument = Instrument.builder()
                            .symbol(symbol)
                            .lastSettlementPrice(price)
                            .volumeTickSize(tickSize)
                            .tradingHours(hours)
                            .build();

                    instruments.put(symbol, instrument);
                    log.info("Loaded instrument: {}", instrument);
                }
            }

            log.info("Loaded {} instruments", instruments.size());
        } catch (IOException e) {
            log.error("Error loading instruments", e);
        }
    }

    public Map<String, Instrument> getAllInstruments() {
        return Collections.unmodifiableMap(instruments);
    }

    public Instrument getInstrument(String symbol) {
        return instruments.get(symbol);
    }

    public boolean isValidInstrument(String symbol) {
        return instruments.containsKey(symbol);
    }

    // Helper method for unit testing
    public void clearAndAddInstrument(Instrument instrument) {
        if (instrument != null && instrument.getSymbol() != null) {
            instruments.put(instrument.getSymbol(), instrument);
        }
    }
}