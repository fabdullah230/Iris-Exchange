package com.iris.iris_dbwriter.repository;

import com.iris.common.model.db.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    // Additional query methods if needed
}