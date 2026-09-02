package com.gateway.gatewayservice.filter;

import com.gateway.gatewayservice.metrics.GatewayMetrics;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "LihuJumIzQKtnoQfCNyxmZ00fDFsbFzib5MTAV771F8=";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private GatewayMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = Mockito.mock(GatewayMetrics.class);
        filter = new JwtAuthenticationFilter(metrics);
        ReflectionTestUtils.setField(filter, "secret", SECRET);

        chain = Mockito.mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void allowsPublicAuthPathWithoutAnyToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void rejectsRequestWithNoAuthorizationHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(metrics, times(1)).recordJwtRejection();
    }

    @Test
    void allowsRequestWithValidToken() {
        String token = validTokenFor("seema", List.of("USER", "ADMIN"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders")
                        .header("Authorization", "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
        verify(metrics, never()).recordJwtRejection();
    }

    @Test
    void rejectsExpiredToken() {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String expiredToken = Jwts.builder()
                .setSubject("seema")
                .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
                .setExpiration(new Date(System.currentTimeMillis() - 5_000)) // already expired
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders")
                        .header("Authorization", "Bearer " + expiredToken)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(metrics, times(1)).recordJwtRejection();
    }

    private String validTokenFor(String username, List<String> roles) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}