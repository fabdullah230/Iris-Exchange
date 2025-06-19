package com.iris.iris_appserver.fix.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.util.List;

@Slf4j
@Component
public class MessageHandlerRegistry {

    private final List<MessageHandler<Message>> handlers;

    public MessageHandlerRegistry(List<MessageHandler<Message>> handlers) {
        this.handlers = handlers;
        log.info("Registered {} FIX message handlers", handlers.size());
    }

    public void handleMessage(Message message, SessionID sessionId) throws FieldNotFound, SessionNotFound {
        String msgType = message.getHeader().getString(quickfix.field.MsgType.FIELD);

        for (MessageHandler<Message> handler : handlers) {
            if (handler.canHandle(msgType)) {
                handler.handle(message, sessionId);
                return;
            }
        }

        log.warn("No handler found for message type: {}", msgType);
        throw new UnsupportedOperationException("Unsupported message type: " + msgType);
    }
}