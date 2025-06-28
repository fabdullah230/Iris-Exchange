package com.iris.common.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reference_prices")
public class ReferencePrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment for primary key
    private Long id;

    private String instrumentId;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database
    private BigDecimal referencePrice;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database

    private BigDecimal priceUpperLimit;
    @Column(precision = 18, scale = 8) // Matches NUMERIC(18, 8) in the database

    private BigDecimal priceLowerLimit;

    private LocalDateTime lastUpdated;

    @Column(name = "sequence_number", nullable = false, unique = true, updatable = false)
    private Long sequenceNumber; // No @GeneratedValue here
}