package com.gateway.gatewayservice.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Sliding window counter: blends the previous fixed window's count with
 * the current one, weighted by how far "now" is into the current window.
 * Smooths out the edge-burst problem a plain fixed window has, without
 * the unbounded storage cost of logging every request timestamp.
 */
@Component
@ConditionalOnProperty(name = "rate-limit.strategy", havingValue = "sliding")
public class RedisSlidingWindowRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisSlidingWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> isAllowed(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowMillis) * windowMillis;
        long previousWindowStart = currentWindowStart - windowMillis;

        String currentKey = "ratelimit:sliding:" + key + ":" + currentWindowStart;
        String previousKey = "ratelimit:sliding:" + key + ":" + previousWindowStart;

        Mono<Long> incrementCurrent = redisTemplate.opsForValue().increment(currentKey)
                .flatMap(count -> {
                    if (count == 1L) {
                        // TTL covers this window PLUS the next one, since
                        // this count still needs to be read as "previous"
                        // for the whole of the next window.
                        return redisTemplate.expire(currentKey, Duration.ofMillis(windowMillis * 2))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                });

        Mono<Long> previousCount = redisTemplate.opsForValue().get(previousKey)
                .map(Long::parseLong)
                .defaultIfEmpty(0L); // no previous window traffic - fine, treat as 0

        return Mono.zip(incrementCurrent, previousCount)
                .map(tuple -> {
                    long currentCount = tuple.getT1();
                    long prevCount = tuple.getT2();

                    double elapsedIntoCurrentWindow = now - currentWindowStart;
                    double weight = 1.0 - (elapsedIntoCurrentWindow / windowMillis);
                    weight = Math.max(0.0, Math.min(1.0, weight)); // clamp for safety

                    double estimatedCount = (prevCount * weight) + currentCount;
                    return estimatedCount <= maxRequests;
                });
    }
}