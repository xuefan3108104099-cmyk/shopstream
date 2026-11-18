package com.shopstream.order.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;

/**
 * Forwards the gateway's X-Request-Id on outgoing Feign calls so that the
 * downstream service logs the very same request id.
 */
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
