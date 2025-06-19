package com.iris.iris_exchangeoperations.controller;

import com.iris.common.model.exchange_operations.Session;
import com.iris.iris_exchangeoperations.service.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManager sessionManager;

    @GetMapping
    public ResponseEntity<Map<String, Session>> getAllSessions() {
        log.info("Request to get all sessions");
        return ResponseEntity.ok(sessionManager.getAllSessions());
    }

    @GetMapping("/{name}")
    public ResponseEntity<Session> getSessionByName(@PathVariable String name) {
        log.info("Request to get session by name: {}", name);
        Session session = sessionManager.getSessionByName(name);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(session);
    }

    @GetMapping("/compid/{compId}")
    public ResponseEntity<Session> getSessionByCompId(@PathVariable String compId) {
        log.info("Request to get session by compId: {}", compId);
        Session session = sessionManager.getSessionByCompId(compId);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(session);
    }

    @GetMapping("/names")
    public ResponseEntity<List<String>> getAllSessionNames() {
        log.info("Request to get all session names");
        List<String> names = sessionManager.getAllSessions().keySet().stream().toList();
        return ResponseEntity.ok(names);
    }

    @GetMapping("/validate/name/{name}")
    public ResponseEntity<Boolean> validateSessionName(@PathVariable String name) {
        log.info("Request to validate session name: {}", name);
        boolean isValid = sessionManager.isValidSession(name);
        return ResponseEntity.ok(isValid);
    }

    @GetMapping("/validate/compid/{compId}")
    public ResponseEntity<Boolean> validateCompId(@PathVariable String compId) {
        log.info("Request to validate compId: {}", compId);
        boolean isValid = sessionManager.isValidCompId(compId);
        return ResponseEntity.ok(isValid);
    }
}