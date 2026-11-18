package com.shopstream.product.events;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Published by order-service on topic "order-events", key = orderId.
 * product-service consumes it and tries to reserve the stock.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(String orderId, String userId, List<OrderLine> lines, BigDecimal totalAmount) {

    public String getType() {
        return "OrderPlaced";
    }
}
