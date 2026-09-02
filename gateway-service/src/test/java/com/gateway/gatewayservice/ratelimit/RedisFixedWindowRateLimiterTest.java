package com.gateway.gatewayservice.ratelimit;

import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Container;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@Testcontainers
class RedisFixedWindowRateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void allowsUpToLimitThenRejects() {
        var connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        var template = new ReactiveStringRedisTemplate(connectionFactory);
        var rateLimiter = new RedisFixedWindowRateLimiter(template);

        String key = "test:redis-container";

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(rateLimiter.isAllowed(key, 3, 60_000))
                    .expectNext(true)
                    .verifyComplete();
        }

        StepVerifier.create(rateLimiter.isAllowed(key, 3, 60_000))
                .expectNext(false)
                .verifyComplete();
    }
}