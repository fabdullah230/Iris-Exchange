package com.iris.iris_appserver.fix;

import com.iris.iris_appserver.fix.handler.MessageHandlerRegistry;
import com.iris.iris_appserver.fix.message.ResponseFactory;
import com.iris.iris_appserver.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.RefMsgType;
import quickfix.field.RefSeqNum;
import quickfix.fix44.Reject;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixApplication implements Application {

    private final MessageHandlerRegistry handlerRegistry;
    private final ResponseFactory responseFactory;
    private final SessionService sessionService;
    private final FixSessionManager fixSessionManager;


    @Override
    public void onCreate(SessionID sessionId) {
        log.info("FIX Session created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {


        String clientSenderCompId = sessionId.getTargetCompID(); // This is the client's identity

        if (!sessionService.isValidSenderCompId(clientSenderCompId)) {
            log.warn("Rejected logon from unauthorized client: {}", clientSenderCompId);
            // The actual reject happens in fromAdmin
            return;
        }


        log.info("FIX Session logged on: {}", sessionId);

        try {
            fixSessionManager.registerSession(clientSenderCompId, sessionId);
            log.info("Successfully registered client {} in session manager", clientSenderCompId);
        } catch (Exception e) {
            log.error("Failed to register session for client {}: {}", clientSenderCompId, e.getMessage());
        }
    }

    @Override
    public void onLogout(SessionID sessionId) {
        String clientSenderCompId = sessionId.getTargetCompID();
        log.info("FIX Session logged out: {}", sessionId);

        // Unregister the session when it logs out
        try {
            fixSessionManager.unregisterSessionBySenderCompId(clientSenderCompId);
            log.info("Successfully unregistered client {} from session manager", clientSenderCompId);
        } catch (Exception e) {
            log.error("Failed to unregister session for client {}: {}", clientSenderCompId, e.getMessage());
        }
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("Sending admin message to client: {}", message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("Received admin message from client: {}, SenderCompID: {}, TargetCompID: {}",
                message, sessionId.getSenderCompID(), sessionId.getTargetCompID());

        // Check if this is a Logon message
        if (message.getHeader().getString(MsgType.FIELD).equals(MsgType.LOGON)) {
            String clientSenderCompId = sessionId.getTargetCompID(); // This is the client's identity
            if (!sessionService.isValidSenderCompId(clientSenderCompId)) {
                log.warn("Rejecting logon from unauthorized sender CompID: {}", clientSenderCompId);
                throw new RejectLogon("Unauthorized sender CompID: " + clientSenderCompId);
            }
        }
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("Sending application message to client: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("Received application message from client: {}", message);
        try {
            handlerRegistry.handleMessage(message, sessionId);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            try {
                sendReject(message, sessionId, "Internal error: " + e.getMessage());
            } catch (SessionNotFound ex) {
                log.error("Could not send reject, session not found: {}", sessionId);
            }
        }
    }

    private void sendReject(Message message, SessionID sessionId, String reason) throws SessionNotFound, FieldNotFound {
        Reject reject = new Reject();

        // Set reference sequence number from original message
        reject.set(new RefSeqNum(message.getHeader().getInt(MsgSeqNum.FIELD)));
        reject.set(new RefMsgType(message.getHeader().getString(MsgType.FIELD)));
        reject.set(new quickfix.field.Text(reason));

        // Send the reject message
        Session.sendToTarget(reject, sessionId);
        log.info("Sent Reject message for MsgType: {}, Reason: {}",
                message.getHeader().getString(MsgType.FIELD), reason);
    }
}