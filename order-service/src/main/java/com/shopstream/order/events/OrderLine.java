package com.shopstream.order.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What product-service needs to know: which product and how many. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderLine(Long productId, Integer quantity) {
}
