package com.iris.common.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "settlement_prices")
public class SettlementPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String instrumentId;
    private LocalDate settlementDate;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database

    private BigDecimal price;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database

    private BigDecimal closingTradeVolume;

}