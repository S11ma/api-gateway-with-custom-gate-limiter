package com.gateway.gatewayservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final Counter totalRequests;
    private final Counter blacklistRejections;
    private final Counter jwtRejections;
    private final Counter apiKeyRejections;
    private final Counter rateLimitRejections;
    private final Counter forwardedRequests;

    public GatewayMetrics(MeterRegistry registry) {
        this.totalRequests = Counter.builder("gateway.requests.total")
                .description("Every request that reached the gateway")
                .register(registry);

        this.blacklistRejections = Counter.builder("gateway.requests.rejected")
                .tag("reason", "blacklist")
                .register(registry);

        this.jwtRejections = Counter.builder("gateway.requests.rejected")
                .tag("reason", "jwt")
                .register(registry);

        this.apiKeyRejections = Counter.builder("gateway.requests.rejected")
                .tag("reason", "api-key")
                .register(registry);

        this.rateLimitRejections = Counter.builder("gateway.requests.rejected")
                .tag("reason", "rate-limit")
                .register(registry);

        this.forwardedRequests = Counter.builder("gateway.requests.forwarded")
                .description("Requests that passed every filter and reached a backend service")
                .register(registry);
    }

    public void recordRequest() {
        totalRequests.increment();
    }

    public void recordBlacklistRejection() {
        blacklistRejections.increment();
    }

    public void recordJwtRejection() {
        jwtRejections.increment();
    }

    public void recordApiKeyRejection() {
        apiKeyRejections.increment();
    }

    public void recordRateLimitRejection() {
        rateLimitRejections.increment();
    }

    public void recordForwarded() {
        forwardedRequests.increment();
    }
}