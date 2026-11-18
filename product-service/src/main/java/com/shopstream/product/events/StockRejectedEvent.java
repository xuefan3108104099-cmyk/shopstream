package com.shopstream.product.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StockRejectedEvent(String orderId, String reason) {

    public String getType() {
        return "StockRejected";
    }
}
