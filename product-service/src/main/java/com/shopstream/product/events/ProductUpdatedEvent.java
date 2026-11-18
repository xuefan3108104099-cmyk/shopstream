package com.shopstream.product.events;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductUpdatedEvent(Long productId, String name, String description, String category,
        BigDecimal price, Integer stock) {

    public String getType() {
        return "ProductUpdated";
    }
}
