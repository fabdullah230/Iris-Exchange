package com.iris.iris_appserver.fix.handler;

import com.iris.iris_appserver.fix.message.ResponseFactory;
import com.iris.iris_appserver.model.ValidationResult;
import com.iris.iris_appserver.service.OrderService;
import com.iris.iris_appserver.validation.OrderValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.OrderCancelReject;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelRequestHandler implements MessageHandler<Message> {

    private final OrderValidationService validationService;
    private final OrderService orderService;
    private final ResponseFactory responseFactory;

    @Override
    public boolean canHandle(String msgType) {
        return MsgType.ORDER_CANCEL_REQUEST.equals(msgType);
    }

    @Override
    public void handle(Message message, SessionID sessionId) throws FieldNotFound, SessionNotFound {
        String origClOrdId = message.getString(OrigClOrdID.FIELD);
        String clOrdId = message.getString(ClOrdID.FIELD);
        String symbol = message.getString(Symbol.FIELD);

        log.info("Received OrderCancelRequest: \nOrigClOrdID={}, \nClOrdID={}, \nSymbol={}, \nSessionID={}",
                origClOrdId, clOrdId, symbol, sessionId);

        // Validate cancel request
        ValidationResult validationResult = validationService.validateOrderCancelRequest(message);
        if (!validationResult.isValid()) {
            log.warn("Cancel request validation failed: {}", validationResult.getReason());
            OrderCancelReject reject = responseFactory.createCancelReject(message, validationResult.getReason());
            Session.sendToTarget(reject, sessionId);
            return;
        }

        // Process valid cancel request
        String orderId = orderService.processCancelOrder(message);
        log.info("Cancel request validated and processed successfully for OrigClOrdID: {}", origClOrdId);

        // Send cancel confirmation to client
        ExecutionReport cancelConfirm = responseFactory.createCancelConfirmation(message, orderId);
        Session.sendToTarget(cancelConfirm, sessionId);
    }
}