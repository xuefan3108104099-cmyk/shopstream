package com.shopstream.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are declared in code with TopicBuilder (requirement of Step 2):
 * three topics, three partitions each, one replica because there is one broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.product-events:product-events}")
    private String productEvents;

    @Value("${app.kafka.topics.order-events:order-events}")
    private String orderEvents;

    @Value("${app.kafka.topics.inventory-events:inventory-events}")
    private String inventoryEvents;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replication-factor:1}")
    private int replicationFactor;

    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(productEvents).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEvents).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(inventoryEvents).partitions(partitions).replicas(replicationFactor).build();
    }
}
