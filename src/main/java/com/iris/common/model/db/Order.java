package com.iris.common.model.db;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String orderId;

    private String clOrdId;
    private String instrumentId;
    private String side;

    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal quantity;

    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal remainingQuantity;

    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal price;

    private String orderType;
    private String timeInForce;
    private String clientId;
    private String sourceIp;

    private LocalDateTime entryTime;
    private LocalDateTime lastUpdatedTime;

    private String status;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> jsonData; // Use a Map to handle structured JSON data
}