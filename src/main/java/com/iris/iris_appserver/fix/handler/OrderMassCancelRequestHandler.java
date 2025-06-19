package com.iris.iris_appserver.fix.handler;

import com.iris.iris_appserver.fix.message.ResponseFactory;
import com.iris.iris_appserver.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.OrderMassCancelReport;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMassCancelRequestHandler implements MessageHandler<Message> {

    private final OrderService orderService;
    private final ResponseFactory responseFactory;

    @Override
    public boolean canHandle(String msgType) {
        return MsgType.ORDER_MASS_CANCEL_REQUEST.equals(msgType);
    }

    @Override
    public void handle(Message message, SessionID sessionId) throws FieldNotFound, SessionNotFound {
        String clOrdId = message.getString(ClOrdID.FIELD);
        int cancelRequestType = message.getInt(MassCancelRequestType.FIELD);

        String symbol = "";
        if (cancelRequestType == MassCancelRequestType.CANCEL_ALL_ORDERS && message.isSetField(Symbol.FIELD)) {
            symbol = message.getString(Symbol.FIELD);
        }

        log.info("Received OrderMassCancelRequest: \nClOrdID={}, \nCancelRequestType={}, \nSymbol={}, \nSessionID={}",
                clOrdId, cancelRequestType, symbol, sessionId);

        // Process mass cancel request
        int totalCanceled = orderService.processMassCancel(message);
        log.info("Mass cancel request processed successfully. Total orders canceled: {}", totalCanceled);

        // Send mass cancel report to client
        OrderMassCancelReport report = responseFactory.createMassCancelReport(message, totalCanceled);
        Session.sendToTarget(report, sessionId);
    }
}