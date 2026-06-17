package com.smartparking.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter: 100 requests per IP per minute.
 * Replace with Redis-backed RequestRateLimiter in Phase 3 when Redis is introduced.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int MAX_REQUESTS_PER_WINDOW = 100;

    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-reset");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(requestCounts::clear, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);
        int count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > MAX_REQUESTS_PER_WINDOW) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        var address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
