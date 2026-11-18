package com.shopstream.cart.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.shopstream.cart.service.CheckoutFailedException;
import com.shopstream.cart.service.EmptyCartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<Map<String, String>> empty(EmptyCartException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CheckoutFailedException.class)
    public ResponseEntity<Map<String, String>> checkoutFailed(CheckoutFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> "invalid field: " + err.getField())
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
