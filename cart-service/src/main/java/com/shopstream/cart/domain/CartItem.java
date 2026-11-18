package com.shopstream.cart.domain;

import java.io.Serializable;
import java.util.Objects;

public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    private Integer quantity;

    protected CartItem() {
    }

    public CartItem(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CartItem item)) {
            return false;
        }
        return Objects.equals(productId, item.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}
