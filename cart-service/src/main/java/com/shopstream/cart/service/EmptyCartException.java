package com.shopstream.cart.service;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException(String message) {
        super(message);
    }
}
