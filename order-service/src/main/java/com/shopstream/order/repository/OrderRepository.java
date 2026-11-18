package com.shopstream.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopstream.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findById(String id);

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
}
