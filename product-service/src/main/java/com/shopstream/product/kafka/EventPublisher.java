package com.shopstream.product.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events as JSON with the entity id as message key, so all events that
 * belong to one order (or one product) land in the same partition and stay ordered.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productEventsTopic;
    private final String inventoryEventsTopic;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.product-events:product-events}") String productEventsTopic,
            @Value("${app.kafka.topics.inventory-events:inventory-events}") String inventoryEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.productEventsTopic = productEventsTopic;
        this.inventoryEventsTopic = inventoryEventsTopic;
    }

    public void publishProduct(Long productId, Object event) {
        send(productEventsTopic, String.valueOf(productId), event);
    }

    public void publishInventory(String orderId, Object event) {
        send(inventoryEventsTopic, orderId, event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} to {} (key={})", event.getClass().getSimpleName(), topic, key, error);
            } else {
                log.debug("Published {} to {} partition {} offset {}", event.getClass().getSimpleName(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
