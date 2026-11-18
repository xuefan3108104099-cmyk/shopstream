package com.shopstream.order.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields order-service actually needs from product-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(Long id, String name, String description, String category, BigDecimal price,
        Integer stock) {
}
