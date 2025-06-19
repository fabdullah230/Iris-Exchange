package com.iris.iris_appserver.fix.handler;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

public interface MessageHandler<T extends Message> {
    void handle(T message, SessionID sessionId) throws FieldNotFound, SessionNotFound;
    boolean canHandle(String msgType);
}