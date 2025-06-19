package com.iris.iris_appserver.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.iris_appserver.fix.FixSessionManager;
import com.iris.iris_appserver.model.messages.ExecutionReportMessage;
import com.iris.iris_appserver.model.messages.MarketDataUpdateMessage;
import com.iris.iris_appserver.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngineConsumer {

    private final ObjectMapper objectMapper;
    private final FixSessionManager fixSessionManager;
    private final InstrumentService instrumentService;

    @KafkaListener(topics = "${app.kafka.topic.outbound-executions:outbound-executions}", groupId = "${spring.application.name}")
    public void consumeExecutionReports(byte[] message, Acknowledgment acknowledgment) {
        try {
            ExecutionReportMessage executionReport = objectMapper.readValue(message, ExecutionReportMessage.class);
            log.info("Received execution report from matching engine: ClientID={}, OrderID={}, ExecType={}",
                    executionReport.getClientId(),
                    executionReport.getExecution().getOrderId(),
                    executionReport.getExecution().getExecType());

            // If this is a trade, update the last trade price in our instrument service
            if ("TRADE".equals(executionReport.getExecution().getExecType()) &&
                    executionReport.getExecution().getLastPrice() != null) {

                instrumentService.updateInstrumentPrice(
                        executionReport.getExecution().getInstrumentId(),
                        executionReport.getExecution().getLastPrice()
                );
            }

            // Convert execution report to FIX message and send to client
            fixSessionManager.sendExecutionReport(executionReport);

            // Acknowledge message processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing execution report", e);
            // Don't acknowledge - message will be redelivered
        }
    }

    @KafkaListener(topics = "${app.kafka.topic.market-data:market-data}", groupId = "${spring.application.name}")
    public void consumeMarketData(byte[] message, Acknowledgment acknowledgment) {
        try {
            MarketDataUpdateMessage marketData = objectMapper.readValue(message, MarketDataUpdateMessage.class);
            log.debug("Received market data update: Symbol={}, LastPrice={}",
                    marketData.getInstrumentId(),
                    marketData.getLastTradePrice());

            // Update instrument service with latest price
            instrumentService.updateInstrumentPrice(
                    marketData.getInstrumentId(),
                    marketData.getLastTradePrice());

            // Acknowledge message processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing market data update", e);
            // Don't acknowledge - message will be redelivered
        }
    }

    // Additional listeners for other topics
    @KafkaListener(topics = "${app.kafka.topic.instrument-updates:instrument-updates}", groupId = "${spring.application.name}")
    public void consumeInstrumentUpdates(byte[] message, Acknowledgment acknowledgment) {
        try {
            // Assuming we have an InstrumentDefinitionMessage class
            // InstrumentDefinitionMessage instrumentDef = objectMapper.readValue(message, InstrumentDefinitionMessage.class);

            // Parse and process the instrument definition
            // This could involve adding new instruments or updating existing ones

            log.info("Processed instrument update");

            // Acknowledge message processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing instrument update", e);
            // Don't acknowledge - message will be redelivered
        }
    }

    @KafkaListener(topics = "${app.kafka.topic.system-control:system-control}", groupId = "${spring.application.name}")
    public void consumeSystemControl(byte[] message, Acknowledgment acknowledgment) {
        try {
            // Process administrative commands
            // This could involve pausing/resuming trading, enabling/disabling instruments, etc.

            log.info("Processed system control message");

            // Acknowledge message processing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing system control message", e);
            // Don't acknowledge - message will be redelivered
        }
    }
}