package com.gateway.gatewayservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "gatewaystats")
public class GatewayStatsEndpoint {

    private final MeterRegistry registry;

    public GatewayStatsEndpoint(MeterRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalRequests", countOf("gateway.requests.total", null));
        result.put("forwardedRequests", countOf("gateway.requests.forwarded", null));

        Map<String, Double> rejections = new LinkedHashMap<>();
        rejections.put("blacklist", countOf("gateway.requests.rejected", "blacklist"));
        rejections.put("jwt", countOf("gateway.requests.rejected", "jwt"));
        rejections.put("apiKey", countOf("gateway.requests.rejected", "api-key"));
        rejections.put("rateLimit", countOf("gateway.requests.rejected", "rate-limit"));
        result.put("rejections", rejections);

        return result;
    }

    private double countOf(String metricName, String reasonTag) {
        var search = registry.find(metricName);
        if (reasonTag != null) {
            search = search.tag("reason", reasonTag);
        }
        var counter = search.counter();
        return counter != null ? counter.count() : 0.0;
    }
}