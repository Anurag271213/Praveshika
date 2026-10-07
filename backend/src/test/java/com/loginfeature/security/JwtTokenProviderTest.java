package com.loginfeature.security;

import com.loginfeature.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private AppProperties appProperties;
    private static final String TEST_SECRET = "0123456789012345678901234567890123456789"; // > 256 bits

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setSecret(TEST_SECRET);
        appProperties.getJwt().setIssuer("test-issuer");
        appProperties.getJwt().setExpirationMs(3600000L); // 1 hour

        jwtTokenProvider = new JwtTokenProvider(appProperties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Generate token creates valid signed JWT with correct subject")
    void generateToken_createsValidToken() {
        String token = jwtTokenProvider.generateToken(42L);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.isTokenValid(token)).isTrue();

        Claims claims = jwtTokenProvider.validateAndGetClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(jwtTokenProvider.getUserIdFromClaims(claims)).isEqualTo(42L);
    }

    @Test
    @DisplayName("TC-010: Invalid JWT is detected and rejected")
    void isTokenValid_returnsFalse_forInvalidToken() {
        assertThat(jwtTokenProvider.isTokenValid("invalid.token.string")).isFalse();
        assertThat(jwtTokenProvider.isTokenValid("")).isFalse();
        assertThat(jwtTokenProvider.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("TC-011: Expired JWT is detected and rejected")
    void isTokenValid_returnsFalse_forExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 100000); // in the past

        String expiredToken = Jwts.builder()
                .subject("99")
                .issuer("test-issuer")
                .issuedAt(new Date(System.currentTimeMillis() - 200000))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThat(jwtTokenProvider.isTokenValid(expiredToken)).isFalse();
    }
}
