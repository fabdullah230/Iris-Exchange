package com.iris.iris_appserver.fix;

import com.iris.iris_appserver.fix.message.ResponseFactory;
import com.iris.iris_appserver.model.Execution;
import com.iris.iris_appserver.model.messages.ExecutionReportMessage;
import com.iris.iris_appserver.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.field.Side;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FixSessionManager {

    private final Map<String, SessionID> clientSessionMap = new ConcurrentHashMap<>();
    private final Map<String, SessionID> senderCompIdSessionMap = new ConcurrentHashMap<>();
    private final ResponseFactory responseFactory;
    private final SessionService sessionService;

    public FixSessionManager(ResponseFactory responseFactory, SessionService sessionService) {
        this.responseFactory = responseFactory;
        this.sessionService = sessionService;
    }

    public void registerSession(String clientId, SessionID sessionID) {
        // Validate client ID against authorized sessions
        if (!sessionService.isValidSenderCompId(clientId)) {
            log.warn("Rejecting unauthorized FIX session for client: {}, SessionID: {}", clientId, sessionID);
            return;
        }


        // If validation passes, register the session
        clientSessionMap.put(clientId, sessionID);
        senderCompIdSessionMap.put(clientId, sessionID);
        log.info("Registered FIX session for client: {}, SessionID: {}", clientId, sessionID);
    }

    public void unregisterSession(String clientId) {
        SessionID sessionID = clientSessionMap.remove(clientId);
        if (sessionID != null) {
            senderCompIdSessionMap.remove(sessionID.getSenderCompID());
            log.info("Unregistered FIX session for client: {}, SessionID: {}", clientId, sessionID);
        }
    }

    public void unregisterSessionBySenderCompId(String senderCompId) {
        SessionID sessionID = senderCompIdSessionMap.remove(senderCompId);
        if (sessionID != null) {
            // Find and remove from clientSessionMap
            clientSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionID));
            log.info("Unregistered FIX session for SenderCompID: {}, SessionID: {}", senderCompId, sessionID);
        }
    }

    public boolean isSessionActive(String clientId) {
        return clientSessionMap.containsKey(clientId);
    }

    public boolean isSessionActiveBySenderCompId(String senderCompId) {
        return senderCompIdSessionMap.containsKey(senderCompId);
    }

    public void sendExecutionReport(ExecutionReportMessage executionReport) {
        try {
            String clientId = executionReport.getClientId();
            SessionID sessionId = senderCompIdSessionMap.get(clientId);

            if (sessionId == null) {
                log.warn("No active FIX session found for client: {}", clientId);
                log.warn("Currently active registered fix sessions are: {}", clientSessionMap.keySet());
                return;
            }

            Execution execution = executionReport.getExecution();
            quickfix.Message fixMessage = translateToFixExecutionReport(execution);

            if (fixMessage == null) {
                log.error("Failed to create FIX message for execution type: {}", execution.getExecType());
                return;
            }

            Session.sendToTarget(fixMessage, sessionId);
            log.info("Sent execution report to client: {}, OrderID: {}, ExecType: {}",
                    clientId, execution.getOrderId(), execution.getExecType());
        } catch (Exception e) {
            log.error("Failed to send execution report", e);
        }
    }

    private quickfix.Message translateToFixExecutionReport(Execution execution) {
        String execType = execution.getExecType();

        switch (execType) {
            case "NEW":
                return responseFactory.createOrderAcknowledgment_from_matching_engine(
                        execution.getClOrdId(),
                        execution.getOrderId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL,
                        execution.getPrice(),
                        execution.getRemainingQuantity()
                );

            case "CANCELED":
                return responseFactory.createCancelConfirmation(
                        execution.getClOrdId(),
                        execution.getOrderId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL
                );

            case "REPLACED":
                return responseFactory.createReplaceConfirmation(
                        execution.getClOrdId(),
                        execution.getOrderId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL,
                        execution.getPrice(),
                        execution.getRemainingQuantity()
                );

            case "REJECTED":
                return responseFactory.createOrderReject(
                        execution.getClOrdId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL,
                        execution.getText()
                );

            case "FILL":
            case "PARTIAL_FILL":
                // Both FILL and PARTIAL_FILL should be translated to TRADE execution reports
                // The difference is in the order status field
                return responseFactory.createTradeReport(
                        execution.getClOrdId(),
                        execution.getOrderId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL,
                        execution.getLastQuantity(),
                        execution.getLastPrice(),
                        execution.getFilledQuantity(),
                        execution.getRemainingQuantity(),
                        execution.getOrderStatus().equals("FILLED") || execType.equals("FILL")
                );


            case "TRADE":
                return responseFactory.createTradeReport(
                        execution.getClOrdId(),
                        execution.getOrderId(),
                        execution.getInstrumentId(),
                        execution.getSide().equals("BUY") ? Side.BUY : Side.SELL,
                        execution.getLastQuantity(),
                        execution.getLastPrice(),
                        execution.getFilledQuantity(),
                        execution.getRemainingQuantity(),
                        execution.getOrderStatus().equals("FILLED")
                );

            default:
                log.warn("Unsupported execution type: {}", execType);
                return null;
        }
    }
}