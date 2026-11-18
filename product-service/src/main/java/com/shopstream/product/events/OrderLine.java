package com.shopstream.product.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One line of an order, as carried on Kafka. Price is intentionally not part of
 * the contract: product-service owns prices, order-service only sends intents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderLine(Long productId, Integer quantity) {
}
