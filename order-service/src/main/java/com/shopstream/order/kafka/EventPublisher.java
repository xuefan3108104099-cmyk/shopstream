package com.shopstream.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes on "order-events" with the orderId as key, so OrderPlaced,
 * OrderConfirmed and OrderRejected of one order are always consumed in order.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String orderEventsTopic;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.order-events:order-events}") String orderEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderEventsTopic = orderEventsTopic;
    }

    public void publish(String orderId, Object event) {
        kafkaTemplate.send(orderEventsTopic, orderId, event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} for order {} to {}", event.getClass().getSimpleName(), orderId,
                        orderEventsTopic, error);
            } else {
                log.info("Published {} for order {} (partition {}, offset {})", event.getClass().getSimpleName(),
                        orderId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
