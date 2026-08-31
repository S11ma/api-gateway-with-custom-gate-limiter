package com.gateway.gatewayservice.ratelimit;

import org.testng.annotations.Test;
import reactor.test.StepVerifier;

class FixedWindowRateLimiterTest {

    private final FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter();

    @Test
    void allowsRequestsUpToTheLimit() {
        String key = "test-user:test-client";

        for (int i = 0; i < 5; i++) {
            StepVerifier.create(rateLimiter.isAllowed(key, 5, 60_000))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Test
    void rejectsRequestBeyondTheLimit() {
        String key = "test-user-2:test-client";

        for (int i = 0; i < 5; i++) {
            rateLimiter.isAllowed(key, 5, 60_000).block();
        }

        StepVerifier.create(rateLimiter.isAllowed(key, 5, 60_000))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void tracksDifferentKeysIndependently() {
        StepVerifier.create(rateLimiter.isAllowed("user-a:client", 1, 60_000))
                .expectNext(true)
                .verifyComplete();

        // A different key should have its own fresh count, unaffected by user-a's.
        StepVerifier.create(rateLimiter.isAllowed("user-b:client", 1, 60_000))
                .expectNext(true)
                .verifyComplete();
    }
}