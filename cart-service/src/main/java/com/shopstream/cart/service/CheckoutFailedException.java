package com.shopstream.cart.service;

public class CheckoutFailedException extends RuntimeException {

    public CheckoutFailedException(String message) {
        super(message);
    }
}
