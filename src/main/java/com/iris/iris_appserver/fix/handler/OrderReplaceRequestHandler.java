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
public class OrderReplaceRequestHandler implements MessageHandler<Message> {

    private final OrderValidationService validationService;
    private final OrderService orderService;
    private final ResponseFactory responseFactory;

    @Override
    public boolean canHandle(String msgType) {
        return MsgType.ORDER_CANCEL_REPLACE_REQUEST.equals(msgType);
    }

    @Override
    public void handle(Message message, SessionID sessionId) throws FieldNotFound, SessionNotFound {
        String origClOrdId = message.getString(OrigClOrdID.FIELD);
        String clOrdId = message.getString(ClOrdID.FIELD);
        String symbol = message.getString(Symbol.FIELD);
        double quantity = message.getDouble(OrderQty.FIELD);
        char orderType = message.getChar(OrdType.FIELD);

        log.info("Received OrderReplaceRequest: \nOrigClOrdID={}, \nClOrdID={}, \nSymbol={}, \nQuantity={}, \nOrdType={}, \nSessionID={}",
                origClOrdId, clOrdId, symbol, quantity, orderType, sessionId);

        // Validate replace request
        ValidationResult validationResult = validationService.validateOrderReplaceRequest(message);
        if (!validationResult.isValid()) {
            log.warn("Replace request validation failed: {}", validationResult.getReason());
            OrderCancelReject reject = responseFactory.createCancelReject(message, validationResult.getReason());
            reject.set(new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REPLACE_REQUEST));
            Session.sendToTarget(reject, sessionId);
            return;
        }

        // Process valid replace request
        String newOrderId = orderService.processReplaceOrder(message);
        log.info("Replace request validated and processed successfully. Generated new OrderID: {}", newOrderId);

        // Send replace confirmation to client
        ExecutionReport replaceConfirm = responseFactory.createReplaceConfirmation(message, newOrderId);
        Session.sendToTarget(replaceConfirm, sessionId);
    }
}