package com.iris.iris_appserver.service;

import com.iris.iris_appserver.engine.MatchingEngineClient;
import com.iris.iris_appserver.model.*;
import com.iris.iris_appserver.model.messages.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MatchingEngineClient matchingEngineClient;

    public String processNewOrder(Message fixMessage) throws FieldNotFound {
        // Extract data from FIX message
        String clientId = fixMessage.getHeader().getString(SenderCompID.FIELD);
        String clOrdId = fixMessage.getString(ClOrdID.FIELD);
        String symbol = fixMessage.getString(Symbol.FIELD);
        char side = fixMessage.getChar(Side.FIELD);
        double quantity = fixMessage.getDouble(OrderQty.FIELD);
        char orderType = fixMessage.getChar(OrdType.FIELD);
        Double price = orderType == OrdType.LIMIT ? fixMessage.getDouble(Price.FIELD) : null;

        // Generate order ID
        String orderId = generateOrderId();

        // Create Order object
        Order order = Order.builder()
                .orderId(orderId)
                .clOrdId(clOrdId)
                .instrumentId(symbol)
                .side(side == Side.BUY ? "BUY" : "SELL")
                .quantity(quantity)
                .orderType(orderTypeToString(orderType))
                .price(price)
                .timeInForce(fixMessage.getString(TimeInForce.FIELD))
                .sourceIpAddress(getClientIpAddress(fixMessage))
                .clientInfo(ClientInfo.builder()
                        .account(fixMessage.isSetField(Account.FIELD) ? fixMessage.getString(Account.FIELD) : null)
                        .userId(clientId)
                        .build())
                .build();

        // Send to matching engine
        matchingEngineClient.sendNewOrder(order);

        return orderId;
    }

    public String processCancelOrder(Message fixMessage) throws FieldNotFound {
        String clientId = fixMessage.getHeader().getString(SenderCompID.FIELD);
        String origClOrdId = fixMessage.getString(OrigClOrdID.FIELD);
        String clOrdId = fixMessage.getString(ClOrdID.FIELD);
        String symbol = fixMessage.getString(Symbol.FIELD);
        char side = fixMessage.getChar(Side.FIELD);

        CancelOrderRequest cancelRequest = CancelOrderRequest.builder()
                .origClOrdId(origClOrdId)
                .clOrdId(clOrdId)
                .instrumentId(symbol)
                .side(side == Side.BUY ? "BUY" : "SELL")
                .sourceIpAddress(getClientIpAddress(fixMessage))
                .build();

        matchingEngineClient.sendCancelOrder(cancelRequest);

        return "ORD" + System.currentTimeMillis(); // Placeholder for response
    }

    public String processReplaceOrder(Message fixMessage) throws FieldNotFound {
        String clientId = fixMessage.getHeader().getString(SenderCompID.FIELD);
        String origClOrdId = fixMessage.getString(OrigClOrdID.FIELD);
        String clOrdId = fixMessage.getString(ClOrdID.FIELD);
        String symbol = fixMessage.getString(Symbol.FIELD);
        char side = fixMessage.getChar(Side.FIELD);
        double quantity = fixMessage.getDouble(OrderQty.FIELD);
        char orderType = fixMessage.getChar(OrdType.FIELD);
        Double price = orderType == OrdType.LIMIT ? fixMessage.getDouble(Price.FIELD) : null;

        String newOrderId = generateOrderId();

        ReplaceOrderRequest replaceRequest = ReplaceOrderRequest.builder()
                .origClOrdId(origClOrdId)
                .clOrdId(clOrdId)
                .newOrderId(newOrderId)
                .instrumentId(symbol)
                .side(side == Side.BUY ? "BUY" : "SELL")
                .quantity(quantity)
                .orderType(orderTypeToString(orderType))
                .price(price)
                .timeInForce(fixMessage.getString(TimeInForce.FIELD))
                .sourceIpAddress(getClientIpAddress(fixMessage))
                .build();

        matchingEngineClient.sendReplaceOrder(replaceRequest);

        return newOrderId;
    }

    public int processMassCancel(Message fixMessage) throws FieldNotFound {
        String clientId = fixMessage.getHeader().getString(SenderCompID.FIELD);
        String clOrdId = fixMessage.getString(ClOrdID.FIELD);
        int massCancelType = fixMessage.getInt(MassCancelRequestType.FIELD);

        String symbol = null;
        if (massCancelType == MassCancelRequestType.CANCEL_ORDERS_FOR_A_PRODUCT &&
                fixMessage.isSetField(Symbol.FIELD)) {
            symbol = fixMessage.getString(Symbol.FIELD);
        }

        MassCancelRequest massCancelRequest = MassCancelRequest.builder()
                .clOrdId(clOrdId)
                .cancelType(massCancelTypeToString(massCancelType))
                .instrumentId(symbol)
                .sourceIpAddress(getClientIpAddress(fixMessage))
                .build();

        matchingEngineClient.sendMassCancel(massCancelRequest);

        // Placeholder - actual count would come from execution report
        return 5;
    }

    // Helper methods
    private String generateOrderId() {
        return "ORD" + System.currentTimeMillis() + "-" +
                java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String orderTypeToString(char orderType) {
        switch (orderType) {
            case OrdType.MARKET: return "MARKET";
            case OrdType.LIMIT: return "LIMIT";
            default: return "UNKNOWN";
        }
    }

    private String massCancelTypeToString(int massCancelType) {
        switch (massCancelType) {
            case MassCancelRequestType.CANCEL_ALL_ORDERS: return "ALL_ORDERS";
            case MassCancelRequestType.CANCEL_ORDERS_FOR_A_PRODUCT: return "BY_PRODUCT";
            default: return "UNKNOWN";
        }
    }

    private String getClientIpAddress(Message message) {
        // In a real implementation, extract IP from session info
        return "127.0.0.1";
    }
}