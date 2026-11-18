package com.shopstream.search.kafka;

import java.math.BigDecimal;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.shopstream.search.domain.ProductDocument;
import com.shopstream.search.repository.ProductSearchRepository;

/**
 * Keeps the "products" index in sync with product-service through
 * "product-events" (consumer group search-service).
 */
@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final ProductSearchRepository repository;

    public ProductEventConsumer(ProductSearchRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.kafka.topics.product-events:product-events}", groupId = "search-service")
    public void onProductEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map<?, ?> payload)) {
            log.warn("Ignoring unexpected product-events payload at offset {}", record.offset());
            return;
        }
        String type = String.valueOf(payload.get("type"));
        Long productId = asLong(payload.get("productId"));
        if (productId == null) {
            log.warn("product-events payload without productId: {}", payload);
            return;
        }

        switch (type) {
            case "ProductCreated", "ProductUpdated" -> {
                ProductDocument document = new ProductDocument(
                        productId,
                        asString(payload.get("name")),
                        asString(payload.get("description")),
                        asString(payload.get("category")),
                        asDecimal(payload.get("price")),
                        asInt(payload.get("stock")));
                repository.save(document);
                log.info("Indexed product {} ({})", productId, type);
            }
            case "ProductDeleted" -> {
                repository.deleteById(productId);
                log.info("Removed product {} from the index", productId);
            }
            default -> log.debug("Ignoring product event of type {}", type);
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal asDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
