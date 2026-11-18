package com.shopstream.order.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** POST /orders body: a list of productId + quantity (price is looked up by order-service). */
public record CreateOrderRequest(String userId, @NotEmpty @Valid List<Line> items) {

    public record Line(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {
    }
}
