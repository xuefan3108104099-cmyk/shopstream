package com.shopstream.cart.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shopstream.cart.client.CheckoutRequest;
import com.shopstream.cart.client.OrderClient;
import com.shopstream.cart.domain.Cart;
import com.shopstream.cart.domain.CartItem;
import com.shopstream.cart.repository.CartRepository;

import feign.FeignException;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository repository;
    private final OrderClient orderClient;
    private final long ttlSeconds;

    public CartService(CartRepository repository, OrderClient orderClient,
            @Value("${app.cart.ttl-seconds:3600}") long ttlSeconds) {
        this.repository = repository;
        this.orderClient = orderClient;
        this.ttlSeconds = ttlSeconds;
    }

    /** Returns the stored cart, or a brand new (not yet persisted) one. */
    public Cart get(String userId) {
        return repository.findById(userId).map(cart -> {
            cart.setTtl(ttlSeconds); // reading refreshes the sliding TTL
            return repository.save(cart);
        }).orElseGet(() -> new Cart(userId, ttlSeconds));
    }

    public Cart addItem(String userId, Long productId, int quantity) {
        Cart cart = get(userId);
        CartItem existing = cart.find(productId);
        int newQuantity = existing == null ? quantity : existing.getQuantity() + quantity;
        cart.put(productId, newQuantity);
        return repository.save(cart);
    }

    public Cart updateQuantity(String userId, Long productId, int quantity) {
        Cart cart = get(userId);
        if (quantity <= 0) {
            cart.remove(productId);
        } else {
            cart.put(productId, quantity);
        }
        return repository.save(cart);
    }

    public Cart removeItem(String userId, Long productId) {
        Cart cart = get(userId);
        cart.remove(productId);
        return repository.save(cart);
    }

    public void clear(String userId) {
        repository.deleteById(userId);
    }

    /**
     * Turns the cart into an order through order-service. The cart is cleared
     * only when the order was accepted (2xx and not REJECTED).
     */
    public CheckoutResult checkout(String userId) {
        Cart cart = get(userId);
        if (cart.isEmpty()) {
            throw new EmptyCartException("cart of user " + userId + " is empty");
        }

        List<CheckoutRequest.Line> lines = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            lines.add(new CheckoutRequest.Line(item.getProductId(), item.getQuantity()));
        }

        CheckoutRequest.OrderResult result;
        try {
            result = orderClient.createOrder(new CheckoutRequest(userId, lines));
        } catch (FeignException ex) {
            log.warn("Checkout for {} failed: {} {}", userId, ex.status(), ex.getMessage());
            throw new CheckoutFailedException("order-service refused the checkout (status " + ex.status() + ")");
        }

        boolean accepted = result != null && !"REJECTED".equalsIgnoreCase(result.status());
        if (accepted) {
            repository.deleteById(userId);
            log.info("Checkout of {} accepted, order {} ({}) - cart cleared", userId, result.id(), result.status());
        } else {
            log.info("Checkout of {} rejected - cart kept for retry", userId);
        }
        return new CheckoutResult(result == null ? null : result.id(),
                result == null ? null : result.status(),
                result == null ? null : result.totalAmount(),
                accepted);
    }

    public record CheckoutResult(String orderId, String status, java.math.BigDecimal totalAmount,
            boolean cartCleared) {
    }
}
