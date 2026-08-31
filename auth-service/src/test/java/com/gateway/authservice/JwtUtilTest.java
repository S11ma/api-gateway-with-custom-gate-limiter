package com.gateway.authservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String TEST_SECRET = "LihuJumIzQKtnoQfCNyxmZ00fDFsbFzib5MTAV771F8=";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Test
    void generateToken_producesTokenWithCorrectSubjectAndRoles() {
        String token = jwtUtil.generateToken("seema", List.of("USER", "ADMIN"));

        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo("seema");
        assertThat(claims.get("roles", List.class)).containsExactly("USER", "ADMIN");
    }

    @Test
    void generateToken_setsExpiryInTheFuture() {
        String token = jwtUtil.generateToken("guest", List.of("USER"));

        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}