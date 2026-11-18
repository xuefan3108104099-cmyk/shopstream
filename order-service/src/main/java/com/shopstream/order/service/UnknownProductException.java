package com.shopstream.order.service;

public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String message) {
        super(message);
    }
}
