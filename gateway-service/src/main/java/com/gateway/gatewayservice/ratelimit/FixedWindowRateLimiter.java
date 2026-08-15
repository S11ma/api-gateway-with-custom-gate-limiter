package com.gateway.gatewayservice.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "rate-limit.strategy", havingValue = "memory", matchIfMissing = true)
public class FixedWindowRateLimiter implements RateLimiter {

    private static class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Boolean> isAllowed(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowMillis) * windowMillis;

        Window window = windows.computeIfAbsent(key, k -> new Window());

        synchronized (window) {
            if (window.windowStart != currentWindowStart) {
                window.windowStart = currentWindowStart;
                window.count.set(0);
            }
            int newCount = window.count.incrementAndGet();
            return Mono.just(newCount <= maxRequests);
        }
    }
}