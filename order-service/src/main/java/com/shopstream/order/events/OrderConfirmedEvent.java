package com.shopstream.order.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(String orderId) {

    public String getType() {
        return "OrderConfirmed";
    }
}
