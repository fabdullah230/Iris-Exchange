package com.iris.iris_exchangeoperations.controller;

import com.iris.common.model.exchange_operations.Instrument;
import com.iris.iris_exchangeoperations.service.InstrumentManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentManager instrumentManager;

    @GetMapping
    public ResponseEntity<Map<String, Instrument>> getAllInstruments() {
        log.info("Request to get all instruments");
        return ResponseEntity.ok(instrumentManager.getAllInstruments());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Instrument> getInstrument(@PathVariable String symbol) {
        log.info("Request to get instrument: {}", symbol);
        Instrument instrument = instrumentManager.getInstrument(symbol);

        if (instrument == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(instrument);
    }

    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getAllSymbols() {
        log.info("Request to get all symbols");
        List<String> symbols = instrumentManager.getAllInstruments().keySet().stream().toList();
        return ResponseEntity.ok(symbols);
    }

    @GetMapping("/validate/{symbol}")
    public ResponseEntity<Boolean> validateInstrument(@PathVariable String symbol) {
        log.info("Request to validate instrument: {}", symbol);
        boolean isValid = instrumentManager.isValidInstrument(symbol);
        return ResponseEntity.ok(isValid);
    }
}