package com.iris.iris_appserver.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.common.kafka.KafkaTopics;
import com.iris.iris_appserver.model.Order;
import com.iris.iris_appserver.model.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class MatchingEngineClient {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final KafkaTopics kafkaTopics;

    public MatchingEngineClient(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper,
            @Qualifier("commonClientId") String clientId,
            KafkaTopics kafkaTopics) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.kafkaTopics = kafkaTopics;
    }

    public void sendNewOrder(Order order) {
        try {
            NewOrderMessage message = NewOrderMessage.builder()
                    .messageType("NewOrder")
                    .messageId("MSG" + UUID.randomUUID().toString().substring(0, 8))
                    .timestamp(Instant.now().toEpochMilli())
                    .clientId(order.getClientInfo().getUserId())
                    .order(order)
                    .build();

            byte[] payload = objectMapper.writeValueAsBytes(message);
            // Use symbol as key for partitioning
            kafkaTemplate.send(kafkaTopics.getInboundOrdersTopic(), order.getInstrumentId(), payload);
            log.info("Sent new order to matching engine: OrderID={}, Symbol={}",
                    order.getOrderId(), order.getInstrumentId());
        } catch (Exception e) {
            log.error("Failed to send new order to matching engine", e);
            throw new RuntimeException("Failed to send order to matching engine", e);
        }
    }

    public void sendCancelOrder(CancelOrderRequest cancelRequest) {
        try {
            CancelOrderMessage message = CancelOrderMessage.builder()
                    .messageType("CancelOrder")
                    .messageId("MSG" + UUID.randomUUID().toString().substring(0, 8))
                    .timestamp(Instant.now().toEpochMilli())
                    .clientId(clientId)
                    .cancel(cancelRequest)
                    .build();

            byte[] payload = objectMapper.writeValueAsBytes(message);
            kafkaTemplate.send(kafkaTopics.getInboundOrdersTopic(), cancelRequest.getInstrumentId(), payload);
            log.info("Sent cancel request to matching engine: OrigOrderID={}, Symbol={}",
                    cancelRequest.getOrigOrderId(), cancelRequest.getInstrumentId());
        } catch (Exception e) {
            log.error("Failed to send cancel request to matching engine", e);
            throw new RuntimeException("Failed to send cancel request to matching engine", e);
        }
    }

    public void sendReplaceOrder(ReplaceOrderRequest replaceRequest) {
        try {
            ReplaceOrderMessage message = ReplaceOrderMessage.builder()
                    .messageType("ReplaceOrder")
                    .messageId("MSG" + UUID.randomUUID().toString().substring(0, 8))
                    .timestamp(Instant.now().toEpochMilli())
                    .clientId(clientId)
                    .replace(replaceRequest)
                    .build();

            byte[] payload = objectMapper.writeValueAsBytes(message);
            kafkaTemplate.send(kafkaTopics.getInboundOrdersTopic(), replaceRequest.getInstrumentId(), payload);
            log.info("Sent replace request to matching engine: OrigOrderID={}, Symbol={}",
                    replaceRequest.getOrigOrderId(), replaceRequest.getInstrumentId());
        } catch (Exception e) {
            log.error("Failed to send replace request to matching engine", e);
            throw new RuntimeException("Failed to send replace request to matching engine", e);
        }
    }

    public void sendMassCancel(MassCancelRequest massCancelRequest) {
        try {
            MassCancelMessage message = MassCancelMessage.builder()
                    .messageType("MassCancel")
                    .messageId("MSG" + UUID.randomUUID().toString().substring(0, 8))
                    .timestamp(Instant.now().toEpochMilli())
                    .clientId(clientId)
                    .massCancel(massCancelRequest)
                    .build();

            byte[] payload = objectMapper.writeValueAsBytes(message);
            String partitionKey = massCancelRequest.getInstrumentId() != null ?
                    massCancelRequest.getInstrumentId() : "ALL";
            kafkaTemplate.send(kafkaTopics.getInboundOrdersTopic(), partitionKey, payload);
            log.info("Sent mass cancel request to matching engine: Type={}, Symbol={}",
                    massCancelRequest.getCancelType(), massCancelRequest.getInstrumentId());
        } catch (Exception e) {
            log.error("Failed to send mass cancel request to matching engine", e);
            throw new RuntimeException("Failed to send mass cancel request to matching engine", e);
        }
    }
}