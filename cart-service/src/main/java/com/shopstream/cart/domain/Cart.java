package com.shopstream.cart.domain;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

/**
 * Step 4: the cart is a Redis hash ("carts:<userId>"), keyed by the userId and
 * removed automatically after the configured TTL (1 hour by default).
 */
@RedisHash("carts")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String userId;

    private Set<CartItem> items = new LinkedHashSet<>();

    @TimeToLive
    private Long ttl;

    protected Cart() {
    }

    public Cart(String userId, long ttlSeconds) {
        this.userId = userId;
        this.items = new LinkedHashSet<>();
        this.ttl = ttlSeconds;
    }

    public CartItem find(Long productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    public void put(Long productId, int quantity) {
        CartItem existing = find(productId);
        if (existing == null) {
            items.add(new CartItem(productId, quantity));
        } else {
            existing.setQuantity(quantity);
        }
    }

    public boolean remove(Long productId) {
        CartItem existing = find(productId);
        return existing != null && items.remove(existing);
    }

    public void clear() {
        items.clear();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public String getUserId() {
        return userId;
    }

    public Set<CartItem> getItems() {
        return items;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(long ttlSeconds) {
        this.ttl = ttlSeconds;
    }
}
