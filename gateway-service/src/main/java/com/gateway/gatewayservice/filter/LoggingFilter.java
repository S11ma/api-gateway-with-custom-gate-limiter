package com.gateway.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        //startTime is captured before chain.filter() runs, so the duration you log later covers the entire downstream pipeline — auth check, routing, the backend service's own response time, everything.//

        log.info("--> {} {} from {}",
                request.getMethod(), request.getURI().getPath(), request.getRemoteAddress());
/*chain.filter(exchange) returns a Mono<Void> that completes when
 the whole rest of the chain (every other filter plus the actual proxied call) is done. It's non-blocking — nothing here holds a thread while waiting.
 .then(Mono.fromRunnable(...)) schedules the "outgoing" log line to run after that
 Mono<Void> completes, regardless of whether the request succeeded or was rejected somewhere downstream (e.g. a 401 from your JWT filter). That's what makes this filter capture rejected requests too, not just successful ones.
 */
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long durationMs = System.currentTimeMillis() - startTime;
            ServerHttpResponse response = exchange.getResponse();
            log.info("<-- {} {} status={} ({} ms)",
                    request.getMethod(), request.getURI().getPath(),
                    response.getStatusCode(), durationMs);
        }));
    }

    @Override
    public int getOrder() {
        return -2; // lower than JwtAuthenticationFilter's -1, so this wraps it
    }
}

