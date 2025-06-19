package com.iris.iris_appserver.service;

import com.iris.iris_appserver.model.Session;
import com.iris.iris_appserver.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.SessionID;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    /**
     * Validates if a sender CompID is authorized to connect
     */
    public boolean isValidSenderCompId(String senderCompId) {
        boolean isValid = sessionRepository.isValidSenderCompId(senderCompId);
        log.debug("Validated sender CompID {}: {}", senderCompId, isValid);
        return isValid;
    }

    

    /**
     * Gets session details by sender CompID
     */
    public Optional<Session> getSessionBySenderCompId(String senderCompId) {
        return sessionRepository.findBySenderCompId(senderCompId);
    }

    /**
     * Gets session details by name
     */
    public Optional<Session> getSessionByName(String name) {
        return sessionRepository.findByName(name);
    }

    /**
     * Gets the clearing account for a sender CompID
     */
    public Optional<String> getClearingAccount(String senderCompId) {
        return getSessionBySenderCompId(senderCompId)
                .map(Session::getClearingAccount);
    }

    /**
     * Gets all sessions
     */
    public Map<String, Session> getAllSessions() {
        return sessionRepository.getAllSessions();
    }

    /**
     * Validates a FIX SessionID by checking the sender CompID
     */
    public boolean isValidSession(SessionID sessionID) {
        String senderCompId = sessionID.getSenderCompID();
        return isValidSenderCompId(senderCompId);
    }
}