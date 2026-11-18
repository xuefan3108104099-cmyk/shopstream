package com.shopstream.product.events;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Published on "product-events", key = productId, when a product is created. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCreatedEvent(Long productId, String name, String description, String category,
        BigDecimal price, Integer stock) {

    public String getType() {
        return "ProductCreated";
    }
}
