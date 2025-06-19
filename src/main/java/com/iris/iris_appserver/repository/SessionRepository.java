package com.iris.iris_appserver.repository;

import com.iris.iris_appserver.model.Session;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class SessionRepository {

    private final Map<String, Session> sessionsByName = new ConcurrentHashMap<>();
    private final Map<String, Session> sessionsBySenderCompId = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${exchange.operations.url:http://localhost:8082}")
    private String exchangeOperationsUrl;

    @PostConstruct
    public void initialize() {
        log.info("Initializing session repository from Exchange Operations API");
        try {
            // Fetch sessions from the Exchange Operations API
            String apiUrl = exchangeOperationsUrl + "/api/sessions";
            Map<String, Map<String, Object>> response = restTemplate.getForObject(
                    apiUrl, Map.class);

            if (response != null && !response.isEmpty()) {
                response.forEach((sessionName, data) -> {
                    try {
                        String name = (String) data.get("name");
                        String fixSenderCompId = (String) data.get("fixSenderCompId");
                        String clearingAccount = (String) data.get("clearingAccount");

                        Session session = new Session(name, fixSenderCompId, clearingAccount);
                        sessionsByName.put(name, session);
                        sessionsBySenderCompId.put(fixSenderCompId, session);

                        log.info("Loaded session from API: {}, SenderCompID: {}, ClearingAccount: {}",
                                name, fixSenderCompId, clearingAccount);
                    } catch (Exception e) {
                        log.error("Error processing session data for {}: {}", sessionName, e.getMessage());
                    }
                });

                log.info("Successfully initialized session repository with {} sessions from API",
                        sessionsByName.size());
            } else {
                log.warn("No sessions received from Exchange Operations API, falling back to sample data");
                initializeWithSampleData();
            }
        } catch (Exception e) {
            log.error("Failed to initialize sessions from API: {}", e.getMessage());
            log.info("Falling back to sample session data");
            initializeWithSampleData();
        }
    }

    private void initializeWithSampleData() {
        // Add some sample sessions as fallback
        addSession("Participant_1", "IRISPAR1", "CLR1");
        addSession("Participant_2", "IRISPAR2", "CLR2");
        addSession("Participant_3", "IRISPAR3", "CLR3");
        log.info("Initialized session repository with {} sample sessions", sessionsByName.size());
    }

    private void addSession(String name, String fixSenderCompId, String clearingAccount) {
        Session session = new Session(name, fixSenderCompId, clearingAccount);
        sessionsByName.put(name, session);
        sessionsBySenderCompId.put(fixSenderCompId, session);
    }

    public Optional<Session> findByName(String name) {
        return Optional.ofNullable(sessionsByName.get(name));
    }

    public Optional<Session> findBySenderCompId(String senderCompId) {
        return Optional.ofNullable(sessionsBySenderCompId.get(senderCompId));
    }

    public boolean isValidSenderCompId(String senderCompId) {
        return sessionsBySenderCompId.containsKey(senderCompId);
    }

    public Map<String, Session> getAllSessions() {
        return new ConcurrentHashMap<>(sessionsByName);
    }
}