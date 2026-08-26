package com.gateway.gatewayservice.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Token bucket: a bucket holding up to `maxRequests` tokens, refilling
 * continuously such that it would take `windowMillis` to go from empty
 * to full. Unlike fixed/sliding window, a quiet client can burst several
 * requests at once, spending accumulated tokens, rather than being
 * smoothed to a rigid per-second rate.
 */
@Component
@ConditionalOnProperty(name = "rate-limit.strategy", havingValue = "token-bucket")
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveHashOperations<String, String, String> hashOps;

    public RedisTokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public Mono<Boolean> isAllowed(String key, int maxRequests, long windowMillis) {
        String redisKey = "ratelimit:bucket:" + key;
        double capacity = maxRequests;
        double refillPerMs = capacity / windowMillis;
        long now = System.currentTimeMillis();

        Mono<String> tokensMono = hashOps.get(redisKey, "tokens")
                .cast(String.class)
                .defaultIfEmpty(String.valueOf(capacity)); // no bucket yet - start full

        Mono<String> lastRefillMono = hashOps.get(redisKey, "ts")
                .cast(String.class)
                .defaultIfEmpty(String.valueOf(now));

        return Mono.zip(tokensMono, lastRefillMono)
                .flatMap(tuple -> {
                    double currentTokens = Double.parseDouble(tuple.getT1());
                    long lastRefillTs = Long.parseLong(tuple.getT2());

                    long elapsed = now - lastRefillTs;
                    double refilled = Math.min(capacity, currentTokens + (elapsed * refillPerMs));

                    boolean allowed = refilled >= 1.0;
                    double remainingTokens = allowed ? refilled - 1.0 : refilled;

                    Map<String, String> updated = Map.of(
                            "tokens", String.valueOf(remainingTokens),
                            "ts", String.valueOf(now)
                    );

                    return hashOps.putAll(redisKey, updated)
                            .then(redisTemplate.expire(redisKey, Duration.ofMillis(windowMillis * 2)))
                            .thenReturn(allowed);
                });
    }
}