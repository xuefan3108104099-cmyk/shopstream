package com.shopstream.notification.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Step 2, consumer side: notification-service listens to BOTH topics in its own
 * consumer group and writes one "email" per final order status.
 * It owns no database - it only reacts to events.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final String fromAddress;

    public NotificationConsumer(@Value("${app.notification.from-address:shopstream@example.com}") String fromAddress) {
        this.fromAddress = fromAddress;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events:order-events}", groupId = "notification-service")
    public void onOrderEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map<?, ?> payload)) {
            return;
        }
        String type = String.valueOf(payload.get("type"));
        String orderId = String.valueOf(payload.get("orderId"));
        switch (type) {
            case "OrderPlaced" -> log.info("EMAIL {} -> user {}: order {} received, we are checking the stock",
                    fromAddress, payload.get("userId"), orderId);
            case "OrderConfirmed" -> log.info("EMAIL {} -> user: your order {} is CONFIRMED",
                    fromAddress, orderId);
            case "OrderRejected" -> log.info("EMAIL {} -> user: your order {} was REJECTED ({})",
                    fromAddress, orderId, payload.get("reason"));
            default -> log.debug("Unhandled order event {}", type);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events:inventory-events}", groupId = "notification-service")
    public void onInventoryEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map<?, ?> payload)) {
            return;
        }
        String type = String.valueOf(payload.get("type"));
        String orderId = String.valueOf(payload.get("orderId"));
        if ("StockReserved".equals(type)) {
            log.info("EMAIL {} -> warehouse: stock reserved for order {}", fromAddress, orderId);
        } else if ("StockRejected".equals(type)) {
            log.info("EMAIL {} -> user: order {} could not be reserved ({})", fromAddress, orderId,
                    payload.get("reason"));
        }
    }
}
