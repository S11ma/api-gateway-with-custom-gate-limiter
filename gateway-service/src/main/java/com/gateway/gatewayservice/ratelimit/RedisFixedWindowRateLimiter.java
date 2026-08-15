package com.gateway.gatewayservice.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "rate-limit.strategy", havingValue = "redis")
public class RedisFixedWindowRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> isAllowed(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowMillis) * windowMillis;

        // One Redis key per (rate-limit key, window). E.g.
        // "ratelimit:seema:mobile-app:1755000000000". A brand new window
        // means a brand new Redis key, so there's nothing to "reset" -
        // it just doesn't exist yet.
        String redisKey = "ratelimit:" + key + ":" + currentWindowStart;

        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1L) {
                        // First request in this window created the key -
                        // set it to expire at the end of the window, so
                        // Redis cleans up old window keys automatically
                        // instead of them accumulating forever.
                        return redisTemplate.expire(redisKey, Duration.ofMillis(windowMillis))
                                .thenReturn(count <= maxRequests);
                    }
                    return Mono.just(count <= maxRequests);
                });
    }
}