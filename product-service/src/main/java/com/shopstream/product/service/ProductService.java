package com.shopstream.product.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopstream.product.domain.ProcessedOrder;
import com.shopstream.product.domain.Product;
import com.shopstream.product.events.OrderLine;
import com.shopstream.product.events.OrderPlacedEvent;
import com.shopstream.product.events.ProductCreatedEvent;
import com.shopstream.product.events.ProductDeletedEvent;
import com.shopstream.product.events.ProductUpdatedEvent;
import com.shopstream.product.kafka.EventPublisher;
import com.shopstream.product.repository.ProcessedOrderRepository;
import com.shopstream.product.repository.ProductRepository;
import com.shopstream.product.web.ProductRequest;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final ProcessedOrderRepository processedOrders;
    private final EventPublisher publisher;

    public ProductService(ProductRepository products, ProcessedOrderRepository processedOrders,
            EventPublisher publisher) {
        this.products = products;
        this.processedOrders = processedOrders;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return products.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategory(String category) {
        return products.findByCategoryIgnoreCase(category);
    }

    /** Cached in Redis - repeated reads of the same product never hit MySQL. */
    @Cacheable(cacheNames = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product " + id + " does not exist"));
    }

    public Product create(ProductRequest request) {
        Product saved = products.save(new Product(request.name(), request.description(), request.category(),
                request.price(), request.stock()));
        publisher.publishProduct(saved.getId(), new ProductCreatedEvent(saved.getId(), saved.getName(),
                saved.getDescription(), saved.getCategory(), saved.getPrice(), saved.getStock()));
        return saved;
    }

    @CacheEvict(cacheNames = "products", key = "#id")
    public Product update(Long id, ProductRequest request) {
        Product product = findById(id);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        Product saved = products.save(product);
        publisher.publishProduct(saved.getId(), new ProductUpdatedEvent(saved.getId(), saved.getName(),
                saved.getDescription(), saved.getCategory(), saved.getPrice(), saved.getStock()));
        return saved;
    }

    @CacheEvict(cacheNames = "products", key = "#id")
    public void delete(Long id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product " + id + " does not exist"));
        products.delete(product);
        publisher.publishProduct(id, new ProductDeletedEvent(id));
    }

    /**
     * Reserve stock for an order in ONE database transaction.
     * Idempotency: the processed_orders table is keyed by orderId, so a duplicate
     * OrderPlaced event finds the key already present and is skipped - stock is
     * never reduced twice.
     */
    @CacheEvict(cacheNames = "products", allEntries = true)
    @Transactional
    public ReservationResult reserveStock(OrderPlacedEvent event) {
        if (event.orderId() == null || event.lines() == null || event.lines().isEmpty()) {
            return ReservationResult.rejectedBecause("order has no lines");
        }
        if (processedOrders.existsById(event.orderId())) {
            log.info("Order {} already processed - duplicate OrderPlaced ignored", event.orderId());
            return ReservationResult.duplicateEvent();
        }

        // aggregate the same product appearing on several lines
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (OrderLine line : event.lines()) {
            if (line.productId() == null || line.quantity() == null || line.quantity() <= 0) {
                return ReservationResult.rejectedBecause("invalid order line for product " + line.productId());
            }
            wanted.merge(line.productId(), line.quantity(), Integer::sum);
        }

        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            Product product = products.findById(entry.getKey()).orElse(null);
            if (product == null) {
                return fail(event.orderId(), "product " + entry.getKey() + " not found");
            }
            if (product.getStock() < entry.getValue()) {
                return fail(event.orderId(),
                        "insufficient stock for product " + entry.getKey() + " (available " + product.getStock()
                                + ", requested " + entry.getValue() + ")");
            }
        }

        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            Product product = products.findById(entry.getKey()).orElseThrow();
            product.decreaseStock(entry.getValue());
            products.save(product);
        }

        processedOrders.save(new ProcessedOrder(event.orderId(), "RESERVED"));
        log.info("Reserved stock for order {} ({} product lines)", event.orderId(), wanted.size());
        return ReservationResult.reservedStock();
    }

    private ReservationResult fail(String orderId, String reason) {
        // remember the decision so a redelivered event does not flip the outcome
        processedOrders.save(new ProcessedOrder(orderId, "REJECTED"));
        log.info("Rejected order {}: {}", orderId, reason);
        return ReservationResult.rejectedBecause(reason);
    }

    /** Small value object returned to the Kafka listener. */
    public record ReservationResult(boolean duplicate, boolean reserved, String reason) {

        public static ReservationResult duplicateEvent() {
            return new ReservationResult(true, false, null);
        }

        public static ReservationResult reservedStock() {
            return new ReservationResult(false, true, null);
        }

        public static ReservationResult rejectedBecause(String reason) {
            return new ReservationResult(false, false, reason);
        }
    }
}
