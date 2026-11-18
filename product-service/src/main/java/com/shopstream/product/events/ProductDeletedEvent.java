package com.shopstream.product.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDeletedEvent(Long productId) {

    public String getType() {
        return "ProductDeleted";
    }
}
