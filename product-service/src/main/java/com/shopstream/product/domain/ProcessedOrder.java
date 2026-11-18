package com.shopstream.product.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Idempotency ledger for stock reservation.
 * The primary key is the orderId, so a duplicated OrderPlaced can never reduce
 * stock twice: the second insert fails and the reservation is skipped.
 */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String orderId;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(nullable = false, length = 32)
    private String outcome;

    protected ProcessedOrder() {
    }

    public ProcessedOrder(String orderId, String outcome) {
        this.orderId = orderId;
        this.outcome = outcome;
        this.processedAt = Instant.now();
    }

    public String getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getOutcome() {
        return outcome;
    }
}
