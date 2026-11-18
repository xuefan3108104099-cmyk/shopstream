package com.shopstream.gateway.filter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Custom global filter required by Step 5: it makes sure every request carries
 * an X-Request-Id header, logs it at the edge, puts it on the response and
 * forwards it downstream so the same id shows up in every service log.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    private final String headerName;

    public RequestIdGlobalFilter(@Value("${app.gateway.request-id-header:X-Request-Id}") String headerName) {
        this.headerName = headerName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(headerName);
        String requestId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(headerName, requestId)
                .build();

        exchange.getResponse().getHeaders().set(headerName, requestId);

        String path = exchange.getRequest().getURI().getPath();
        log.info("[{}] gateway received {} {}", requestId, exchange.getRequest().getMethod(), path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // must run before the routing filters so the header exists when the call is made
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
