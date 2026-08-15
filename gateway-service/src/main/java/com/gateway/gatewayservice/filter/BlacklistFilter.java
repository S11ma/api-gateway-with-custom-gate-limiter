package com.gateway.gatewayservice.filter;

import com.gateway.gatewayservice.config.BlacklistProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Component
public class BlacklistFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final BlacklistProperties blacklistProperties;

    public BlacklistFilter(BlacklistProperties blacklistProperties) {
        this.blacklistProperties = blacklistProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String callerIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : null;

        if (callerIp != null && blacklistProperties.getIps().contains(callerIp)) {
            return forbidden(exchange, "Your IP address has been blocked");
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey != null && blacklistProperties.getApiKeys().contains(apiKey)) {
            return forbidden(exchange, "This API key has been revoked");
        }

        return chain.filter(exchange);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        byte[] bytes = ("{\"error\": \"" + reason + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -2; // after LoggingFilter (-3), before JwtAuthenticationFilter (-1)
    }
}
