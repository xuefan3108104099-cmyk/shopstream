package com.shopstream.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopstream.product.domain.ProcessedOrder;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, String> {
}
