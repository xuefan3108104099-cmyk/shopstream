package com.shopstream.order.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRejectedEvent(String orderId, String reason) {

    public String getType() {
        return "OrderRejected";
    }
}
