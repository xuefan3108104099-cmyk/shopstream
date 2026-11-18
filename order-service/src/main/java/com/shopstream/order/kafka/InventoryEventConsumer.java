package com.shopstream.order.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopstream.order.events.StockRejectedEvent;
import com.shopstream.order.events.StockReservedEvent;
import com.shopstream.order.service.OrderService;

/**
 * Step 2, direction 2: order-service consumes the answer from product-service and
 * moves the order from PENDING to CONFIRMED or REJECTED.
 */
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events:inventory-events}", groupId = "order-service")
    public void onInventoryEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map<?, ?> payload)) {
            log.warn("Ignoring unexpected inventory-events payload at offset {}: {}", record.offset(), record.value());
            return;
        }
        String type = String.valueOf(payload.get("type"));
        switch (type) {
            case "StockReserved" -> {
                StockReservedEvent event = objectMapper.convertValue(payload, StockReservedEvent.class);
                orderService.onStockReserved(event.orderId());
            }
            case "StockRejected" -> {
                StockRejectedEvent event = objectMapper.convertValue(payload, StockRejectedEvent.class);
                orderService.onStockRejected(event.orderId(), event.reason());
            }
            default -> log.debug("Ignoring inventory event of type {}", type);
        }
    }
}
