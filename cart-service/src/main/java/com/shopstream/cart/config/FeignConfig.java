package com.shopstream.cart.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;

/** Forwards X-Request-Id to order-service during checkout. */
@Configuration
public class FeignConfig {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> {
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                template.header(REQUEST_ID_HEADER, requestId);
            }
        };
    }
}
