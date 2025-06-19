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

@Slf4j
@Component
@RequiredArgsConstructor
public class NewOrderSingleHandler implements MessageHandler<Message> {

    private final OrderValidationService validationService;
    private final OrderService orderService;
    private final ResponseFactory responseFactory;

    @Override
    public boolean canHandle(String msgType) {
        return MsgType.ORDER_SINGLE.equals(msgType);
    }

    @Override
    public void handle(Message message, SessionID sessionId) throws FieldNotFound, SessionNotFound {
        String clOrdId = message.getString(ClOrdID.FIELD);
        String symbol = message.getString(Symbol.FIELD);
        char side = message.getChar(Side.FIELD);
        double quantity = message.getDouble(OrderQty.FIELD);
        char orderType = message.getChar(OrdType.FIELD);

        log.info("Received NewOrderSingle: \nClOrdID={}, \nSymbol={}, \nSide={}, \nQuantity={}, \nOrdType={}, \nSessionID={}",
                clOrdId, symbol, side, quantity, orderType, sessionId);

        // Validate order
        ValidationResult validationResult = validationService.validateNewOrderSingle(message);
        if (!validationResult.isValid()) {
            log.warn("Order validation failed: {}", validationResult.getReason());
            ExecutionReport reject = responseFactory.createOrderReject(message, validationResult.getReason());
            Session.sendToTarget(reject, sessionId);
            return;
        }

        // Process valid order
        //this is where you process imp and send to me and handle response from me
        String orderId = orderService.processNewOrder(message);
        log.info("Order validated and processed successfully. Generated OrderID: {}", orderId);

        // Send acknowledgment to client
        ExecutionReport ack = responseFactory.createOrderAcknowledgment_from_appserver(message, orderId);
        Session.sendToTarget(ack, sessionId);
    }
}