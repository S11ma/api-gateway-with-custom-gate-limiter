package com.gateway.gatewayservice.filter;

import com.gateway.gatewayservice.config.RateLimiterProperties;
import com.gateway.gatewayservice.metrics.GatewayMetrics;
import com.gateway.gatewayservice.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runs after both auth filters, so X-Auth-Username and X-Client-Name are
 * already resolved and available on the request. The rate limit key
 * combines both - "seema:mobile-app" - so limits are tracked per
 * user-per-client, not just per user or just per client.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of("/auth/");

    private final RateLimiter rateLimiter;
    private final RateLimiterProperties properties;
    private final GatewayMetrics metrics;

    public RateLimitingFilter(RateLimiter rateLimiter, RateLimiterProperties properties, GatewayMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String username = exchange.getRequest().getHeaders().getFirst("X-Auth-Username");
        String clientName = exchange.getRequest().getHeaders().getFirst("X-Client-Name");
        String rateLimitKey = (username != null ? username : "anonymous")
                + ":" + (clientName != null ? clientName : "unknown-client");

        long windowMillis = properties.getWindowSeconds() * 1000L;

        return rateLimiter.isAllowed(rateLimitKey, properties.getMaxRequests(), windowMillis)
                .flatMap(allowed -> allowed
                        ? chain.filter(exchange)
                        : recordAndReject(exchange, rateLimitKey));
    }

    private Mono<Void> recordAndReject(ServerWebExchange exchange, String key) {
        metrics.recordRateLimitRejection();
        return tooManyRequests(exchange, key);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String key) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"error\": \"Rate limit exceeded for " + key + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return 1; // after ApiKeyAuthenticationFilter (0), before route forwarding
    }
}