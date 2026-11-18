package com.shopstream.cart.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopstream.cart.domain.Cart;
import com.shopstream.cart.service.CartService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping("/{userId}")
    public Cart get(@PathVariable String userId) {
        return service.get(userId);
    }

    @PostMapping("/{userId}/items")
    public Cart addItem(@PathVariable String userId, @Valid @RequestBody AddItemRequest request) {
        return service.addItem(userId, request.productId(), request.quantity());
    }

    @PutMapping("/{userId}/items/{productId}")
    public Cart updateQuantity(@PathVariable String userId, @PathVariable Long productId,
            @Valid @RequestBody QuantityRequest request) {
        return service.updateQuantity(userId, productId, request.quantity());
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public Cart removeItem(@PathVariable String userId, @PathVariable Long productId) {
        return service.removeItem(userId, productId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empty(@PathVariable String userId) {
        service.clear(userId);
    }

    @PostMapping("/{userId}/checkout")
    public CartService.CheckoutResult checkout(@PathVariable String userId) {
        return service.checkout(userId);
    }

    public record AddItemRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {
    }

    public record QuantityRequest(@NotNull @Min(0) Integer quantity) {
    }
}
