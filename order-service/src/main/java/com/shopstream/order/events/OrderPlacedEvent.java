package com.shopstream.order.events;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(String orderId, String userId, List<OrderLine> lines, BigDecimal totalAmount) {

    public String getType() {
        return "OrderPlaced";
    }
}
