package com.gateway.gatewayservice.ratelimit;

import reactor.core.publisher.Mono;

public interface RateLimiter {
    Mono<Boolean> isAllowed(String key, int maxRequests, long windowMillis);
}