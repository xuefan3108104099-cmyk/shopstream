package com.shopstream.product.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopstream.product.events.OrderPlacedEvent;
import com.shopstream.product.events.StockRejectedEvent;
import com.shopstream.product.events.StockReservedEvent;
import com.shopstream.product.service.ProductService;

/**
 * Consumes "order-events" in the product-service consumer group and reserves
 * the stock. The stock change and the idempotency record commit together, then
 * the answer is published on "inventory-events".
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProductService productService;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ProductService productService, EventPublisher publisher, ObjectMapper objectMapper) {
        this.productService = productService;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events:order-events}", groupId = "product-service")
    public void onOrderEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map<?, ?> payload)) {
            log.warn("Ignoring unexpected order-events payload at offset {}: {}", record.offset(), record.value());
            return;
        }
        String type = String.valueOf(payload.get("type"));
        if (!"OrderPlaced".equals(type)) {
            // OrderConfirmed / OrderRejected are meaningful for order-service only.
            return;
        }

        OrderPlacedEvent event = objectMapper.convertValue(payload, OrderPlacedEvent.class);
        log.info("Received OrderPlaced for order {} (key={}, partition={})", event.orderId(), record.key(),
                record.partition());

        ProductService.ReservationResult result = productService.reserveStock(event);
        if (result.duplicate()) {
            return;
        }
        if (result.reserved()) {
            publisher.publishInventory(event.orderId(), new StockReservedEvent(event.orderId()));
        } else {
            publisher.publishInventory(event.orderId(), new StockRejectedEvent(event.orderId(), result.reason()));
        }
    }
}
