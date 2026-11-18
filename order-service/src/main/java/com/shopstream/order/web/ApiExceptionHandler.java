package com.shopstream.order.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.shopstream.order.service.CatalogUnavailableException;
import com.shopstream.order.service.OrderNotFoundException;
import com.shopstream.order.service.UnknownProductException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnknownProductException.class)
    public ResponseEntity<Map<String, String>> unknownProduct(UnknownProductException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Map<String, String>> catalogueDown(CatalogUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> "invalid field: " + err.getField() + " (" + err.getDefaultMessage() + ")")
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
