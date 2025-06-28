package com.iris.iris_dbwriter.repository;

import com.iris.common.model.db.OrderBookState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderBookStateRepository extends JpaRepository<OrderBookState, Long> {
    // Additional query methods if needed
}