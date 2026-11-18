package com.shopstream.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Step 5: the URL is not hard-coded any more - only the service name. Feign +
 * Eureka + Spring Cloud LoadBalancer pick a live instance (there are two
 * product-service instances in docker-compose).
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);
}
