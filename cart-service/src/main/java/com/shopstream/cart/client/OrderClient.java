package com.shopstream.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Step 5: cart-service finds order-service by name, never by URL.
 */
@FeignClient(name = "order-service")
public interface OrderClient {

    @PostMapping("/orders")
    CheckoutRequest.OrderResult createOrder(@RequestBody CheckoutRequest request);
}
