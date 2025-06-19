package com.iris.iris_exchangeoperations.service;

import com.iris.common.model.exchange_operations.Session;
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
public class SessionManager {

    @Value("${exchange.sessions.file:sessions.csv}")
    private String sessionsFilePath;
    private final ResourceLoader resourceLoader;
    private final Map<String, Session> sessionsByName = new ConcurrentHashMap<>();
    private final Map<String, Session> sessionsByCompId = new ConcurrentHashMap<>();

    public SessionManager(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadSessions() {
        log.info("Loading sessions from: {}", sessionsFilePath);

        Resource resource = resourceLoader.getResource("classpath:" + sessionsFilePath);

        if (!resource.exists()) {
            log.warn("Sessions file not found: {}", sessionsFilePath);
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
                if (parts.length >= 3) {
                    String name = parts[0].trim();
                    String compId = parts[1].trim();
                    String clearingAccount = parts[2].trim();

                    Session session = Session.builder()
                            .name(name)
                            .fixSenderCompId(compId)
                            .clearingAccount(clearingAccount)
                            .build();

                    sessionsByName.put(name, session);
                    sessionsByCompId.put(compId, session);
                    log.info("Loaded session: {}", session);
                }
            }

            log.info("Loaded {} sessions", sessionsByName.size());
        } catch (IOException e) {
            log.error("Error loading sessions", e);
        }
    }

    public Map<String, Session> getAllSessions() {
        return Collections.unmodifiableMap(sessionsByName);
    }

    public Session getSessionByName(String name) {
        return sessionsByName.get(name);
    }

    public Session getSessionByCompId(String compId) {
        return sessionsByCompId.get(compId);
    }

    public boolean isValidSession(String name) {
        return sessionsByName.containsKey(name);
    }

    public boolean isValidCompId(String compId) {
        return sessionsByCompId.containsKey(compId);
    }

    // Helper method for unit testing
    public void clearAndAddSession(Session session) {
        if (session != null && session.getName() != null && session.getFixSenderCompId() != null) {
            sessionsByName.put(session.getName(), session);
            sessionsByCompId.put(session.getFixSenderCompId(), session);
        }
    }
}