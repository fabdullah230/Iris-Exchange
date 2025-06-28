package com.iris.common.model.db;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orderbook_state")
public class OrderBookState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String instrumentId;
    private LocalDateTime timestamp;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal bestBidPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal bestBidQuantity;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal secondBidPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal secondBidQuantity;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal thirdBidPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal thirdBidQuantity;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal bestAskPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal bestAskQuantity;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal secondAskPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal secondAskQuantity;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal thirdAskPrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal thirdAskQuantity;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String bidsJson;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String asksJson;
}