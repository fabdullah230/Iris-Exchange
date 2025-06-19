package com.iris.iris_appserver.fix.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.OrderCancelReject;
import quickfix.fix44.OrderMassCancelReport;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class ResponseFactory {

    // Existing method that takes a Message
    public ExecutionReport createOrderAcknowledgment_from_appserver(Message orderMessage, String orderId) throws FieldNotFound {
        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.PENDING_NEW),
                new OrdStatus(OrdStatus.PENDING_NEW),
                new Side(orderMessage.getChar(Side.FIELD)),
                new LeavesQty(orderMessage.getDouble(OrderQty.FIELD)),
                new CumQty(0),
                new AvgPx(0)
        );

        // Copy required fields from the original order
        executionReport.set(new ClOrdID(orderMessage.getString(ClOrdID.FIELD)));
        executionReport.set(new Symbol(orderMessage.getString(Symbol.FIELD)));
        executionReport.set(new OrderQty(orderMessage.getDouble(OrderQty.FIELD)));

        if (orderMessage.getChar(OrdType.FIELD) == OrdType.LIMIT && orderMessage.isSetField(Price.FIELD)) {
            executionReport.set(new Price(orderMessage.getDouble(Price.FIELD)));
        }

        executionReport.set(new TransactTime(getCurrentUtcDateTime()));
        return executionReport;
    }

    // Add this overloaded method that takes individual parameters
    public ExecutionReport createOrderAcknowledgment_from_matching_engine(
            String clOrdId, String orderId, String symbol, char side,
            Double price, double quantity) {

        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.NEW),
                new OrdStatus(OrdStatus.NEW),
                new Side(side),
                new LeavesQty(quantity),
                new CumQty(0),
                new AvgPx(0)
        );

        executionReport.set(new ClOrdID(clOrdId));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new OrderQty(quantity));

        if (price != null) {
            executionReport.set(new Price(price));
            executionReport.set(new OrdType(OrdType.LIMIT));
        } else {
            executionReport.set(new OrdType(OrdType.MARKET));
        }

        executionReport.set(new TransactTime(getCurrentUtcDateTime()));
        return executionReport;
    }

    // Similar overloaded methods for the other response types
    public ExecutionReport createOrderReject(Message orderMessage, String reason) throws FieldNotFound {
        ExecutionReport executionReport = new ExecutionReport(
                new OrderID("NONE"),
                new ExecID(generateExecId()),
                new ExecType(ExecType.REJECTED),
                new OrdStatus(OrdStatus.REJECTED),
                new Side(orderMessage.getChar(Side.FIELD)),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
        );

        executionReport.set(new ClOrdID(orderMessage.getString(ClOrdID.FIELD)));
        executionReport.set(new Symbol(orderMessage.getString(Symbol.FIELD)));
        executionReport.set(new OrderQty(orderMessage.getDouble(OrderQty.FIELD)));
        executionReport.set(new Text(reason));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    // Overloaded method for order reject
    public ExecutionReport createOrderReject(
            String clOrdId, String symbol, char side, String reason) {

        ExecutionReport executionReport = new ExecutionReport(
                new OrderID("NONE"),
                new ExecID(generateExecId()),
                new ExecType(ExecType.REJECTED),
                new OrdStatus(OrdStatus.REJECTED),
                new Side(side),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
        );

        executionReport.set(new ClOrdID(clOrdId));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new Text(reason));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    public ExecutionReport createCancelConfirmation(Message cancelMessage, String orderId) throws FieldNotFound {
        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.CANCELED),
                new OrdStatus(OrdStatus.CANCELED),
                new Side(cancelMessage.getChar(Side.FIELD)),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
        );

        executionReport.set(new OrigClOrdID(cancelMessage.getString(OrigClOrdID.FIELD)));
        executionReport.set(new ClOrdID(cancelMessage.getString(ClOrdID.FIELD)));
        executionReport.set(new Symbol(cancelMessage.getString(Symbol.FIELD)));
        executionReport.set(new Text("Order canceled successfully"));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    // Overloaded method for cancel confirmation
    public ExecutionReport createCancelConfirmation(
            String clOrdId, String orderId, String symbol, char side) {

        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.CANCELED),
                new OrdStatus(OrdStatus.CANCELED),
                new Side(side),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
        );

        // Note: We don't have the original clOrdId here, which would normally be set in OrigClOrdID
        // This is a limitation of this interface
        executionReport.set(new ClOrdID(clOrdId));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new Text("Order canceled successfully"));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    public OrderCancelReject createCancelReject(Message cancelMessage, String reason) throws FieldNotFound {
        OrderCancelReject orderCancelReject = new OrderCancelReject(
                new OrderID("NONE"),
                new ClOrdID(cancelMessage.getString(ClOrdID.FIELD)),
                new OrigClOrdID(cancelMessage.getString(OrigClOrdID.FIELD)),
                new OrdStatus(OrdStatus.REJECTED),
                new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST)
        );

        orderCancelReject.set(new Text(reason));
        orderCancelReject.set(new CxlRejReason(CxlRejReason.OTHER));
        orderCancelReject.set(new TransactTime(getCurrentUtcDateTime()));

        return orderCancelReject;
    }

    public ExecutionReport createReplaceConfirmation(Message replaceMessage, String newOrderId) throws FieldNotFound {
        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(newOrderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.REPLACED),
                new OrdStatus(OrdStatus.REPLACED),
                new Side(replaceMessage.getChar(Side.FIELD)),
                new LeavesQty(replaceMessage.getDouble(OrderQty.FIELD)),
                new CumQty(0),
                new AvgPx(0)
        );

        // Set the original and current ClOrdID
        executionReport.set(new OrigClOrdID(replaceMessage.getString(OrigClOrdID.FIELD)));
        executionReport.set(new ClOrdID(replaceMessage.getString(ClOrdID.FIELD)));
        executionReport.set(new Symbol(replaceMessage.getString(Symbol.FIELD)));
        executionReport.set(new OrderQty(replaceMessage.getDouble(OrderQty.FIELD)));

        if (replaceMessage.getChar(OrdType.FIELD) == OrdType.LIMIT && replaceMessage.isSetField(Price.FIELD)) {
            executionReport.set(new Price(replaceMessage.getDouble(Price.FIELD)));
        }

        executionReport.set(new Text("Order replaced successfully"));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    // Overloaded method for replace confirmation
    public ExecutionReport createReplaceConfirmation(
            String clOrdId, String orderId, String symbol, char side,
            Double price, double quantity) {

        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.REPLACED),
                new OrdStatus(OrdStatus.REPLACED),
                new Side(side),
                new LeavesQty(quantity),
                new CumQty(0),
                new AvgPx(0)
        );

        // Note: We don't have the original clOrdId here, which would normally be set in OrigClOrdID
        executionReport.set(new ClOrdID(clOrdId));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new OrderQty(quantity));

        if (price != null) {
            executionReport.set(new Price(price));
        }

        executionReport.set(new Text("Order replaced successfully"));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    public OrderMassCancelReport createMassCancelReport(Message cancelMessage, int totalCanceled) throws FieldNotFound {
        OrderMassCancelReport report = new OrderMassCancelReport(
                new OrderID(cancelMessage.getString(ClOrdID.FIELD)),
                new MassCancelRequestType(MassCancelRequestType.CANCEL_ALL_ORDERS),
                new MassCancelResponse(MassCancelResponse.CANCEL_ALL_ORDERS)
        );

        // Set symbol if present in the request
        if (cancelMessage.isSetField(Symbol.FIELD)) {
            report.set(new Symbol(cancelMessage.getString(Symbol.FIELD)));
        }

        // Set side if present in the request
        if (cancelMessage.isSetField(Side.FIELD)) {
            report.set(new Side(cancelMessage.getChar(Side.FIELD)));
        }

        report.set(new TotalAffectedOrders(totalCanceled));
        report.set(new TransactTime(getCurrentUtcDateTime()));
        report.set(new Text("Mass cancel processed successfully"));

        return report;
    }

    public ExecutionReport createTradeReport(
            String clOrdId, String orderId, String symbol, char side,
            double lastQty, double lastPx, double cumQty, double leavesQty, boolean isFilled) {

        char ordStatus = isFilled ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED;

        ExecutionReport executionReport = new ExecutionReport(
                new OrderID(orderId),
                new ExecID(generateExecId()),
                new ExecType(ExecType.TRADE),
                new OrdStatus(ordStatus),
                new Side(side),
                new LeavesQty(leavesQty),
                new CumQty(cumQty),
                new AvgPx(lastPx)
        );

        // Set required fields
        executionReport.set(new ClOrdID(clOrdId));
        executionReport.set(new Symbol(symbol));
        executionReport.set(new LastQty(lastQty));
        executionReport.set(new LastPx(lastPx));
        executionReport.set(new TransactTime(getCurrentUtcDateTime()));

        return executionReport;
    }

    private String generateExecId() {
        return "EXE" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private LocalDateTime getCurrentUtcDateTime() {
        return LocalDateTime.now();
    }
}