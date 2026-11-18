package com.shopstream.cart.client;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body sent to order-service POST /orders (same shape the gateway exposes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutRequest(String userId, List<Line> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long productId, Integer quantity) {
    }

    /** What comes back from order-service; we only need the outcome. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderResult(String id, String status, BigDecimal totalAmount) {
    }
}
