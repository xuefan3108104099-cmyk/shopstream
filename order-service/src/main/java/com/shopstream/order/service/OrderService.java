package com.shopstream.order.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shopstream.order.client.ProductClient;
import com.shopstream.order.client.ProductDto;
import com.shopstream.order.domain.Order;
import com.shopstream.order.domain.OrderItem;
import com.shopstream.order.domain.OrderStatus;
import com.shopstream.order.events.OrderConfirmedEvent;
import com.shopstream.order.events.OrderLine;
import com.shopstream.order.events.OrderPlacedEvent;
import com.shopstream.order.events.OrderRejectedEvent;
import com.shopstream.order.kafka.EventPublisher;
import com.shopstream.order.repository.OrderRepository;
import com.shopstream.order.web.CreateOrderRequest;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ProductClient productClient;
    private final EventPublisher publisher;

    public OrderService(OrderRepository orders, ProductClient productClient, EventPublisher publisher) {
        this.orders = orders;
        this.productClient = productClient;
        this.publisher = publisher;
    }

    /**
     * Create the order as PENDING, then publish OrderPlaced. Reading prices goes
     * through Feign by service name - order-service never touches product tables.
     */
    @Transactional
    public Order placeOrder(CreateOrderRequest request) {
        List<OrderItem> items = new ArrayList<>();
        List<OrderLine> eventLines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.Line line : request.items()) {
            ProductDto product;
            try {
                product = productClient.getProduct(line.productId());
            } catch (feign.FeignException.NotFound notFound) {
                throw new UnknownProductException("product " + line.productId() + " does not exist");
            } catch (feign.FeignException ex) {
                throw new CatalogUnavailableException("product-service call failed: " + ex.getMessage());
            }
            if (product.price() == null) {
                throw new CatalogUnavailableException("product " + line.productId() + " has no price");
            }
            items.add(new OrderItem(product.id(), product.name(), product.price(), line.quantity()));
            eventLines.add(new OrderLine(product.id(), line.quantity()));
            total = total.add(product.price().multiply(BigDecimal.valueOf(line.quantity())));
        }

        String userId = (request.userId() == null || request.userId().isBlank()) ? "anonymous" : request.userId();
        Order order = new Order(UUID.randomUUID().toString(), userId, items, total);
        Order saved = orders.save(order);

        OrderPlacedEvent event = new OrderPlacedEvent(saved.getId(), userId, eventLines, total);
        // publish only after the PENDING order is committed
        afterCommit(() -> publisher.publish(saved.getId(), event));
        log.info("Placed order {} for {} with {} line(s), total {}", saved.getId(), userId, items.size(), total);
        return saved;
    }

    @Transactional(readOnly = true)
    public Order get(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException("order " + orderId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Order> listForUser(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** inventory-events / StockReserved -> CONFIRMED. Duplicate events are ignored. */
    @Transactional
    public void onStockReserved(String orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("StockReserved for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {} - duplicate StockReserved ignored", orderId, order.getStatus());
            return;
        }
        order.confirm();
        orders.save(order);
        afterCommit(() -> publisher.publish(orderId, new OrderConfirmedEvent(orderId)));
        log.info("Order {} CONFIRMED", orderId);
    }

    /** inventory-events / StockRejected -> REJECTED. */
    @Transactional
    public void onStockRejected(String orderId, String reason) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("StockRejected for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {} - duplicate StockRejected ignored", orderId, order.getStatus());
            return;
        }
        order.reject(reason == null ? "stock could not be reserved" : reason);
        orders.save(order);
        afterCommit(() -> publisher.publish(orderId,
                new OrderRejectedEvent(orderId, order.getRejectionReason())));
        log.info("Order {} REJECTED: {}", orderId, order.getRejectionReason());
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
