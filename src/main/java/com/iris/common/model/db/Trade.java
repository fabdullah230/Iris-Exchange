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
@Table(name = "trades")
public class Trade {
    @Id
    private String tradeId;

    private String instrumentId;

    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in PostgreSQL
    private BigDecimal price;

    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in PostgreSQL
    private BigDecimal quantity;

    private String buyerOrderId;
    private String sellerOrderId;
    private String buyerClOrdId;
    private String sellerClOrdId;
    private String buyerClientId;
    private String sellerClientId;

    private LocalDateTime tradeTime;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String jsonData;
}