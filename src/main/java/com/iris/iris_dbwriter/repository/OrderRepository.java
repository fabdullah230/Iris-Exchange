package com.iris.iris_dbwriter.repository;

import com.iris.common.model.db.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    // Additional query methods if needed
}