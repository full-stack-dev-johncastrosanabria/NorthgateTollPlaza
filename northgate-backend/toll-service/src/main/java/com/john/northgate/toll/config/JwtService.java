package com.john.northgate.toll.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${northgate.jwt.secret}") String secret,
                      @Value("${northgate.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(String staffCode, String fullName, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(staffCode)
                .claim("name", fullName)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * Short-lived token identifying this service to audit-service. Both services
     * share the HS256 secret, so no auth round-trip is needed.
     */
    public String issueServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("toll-service")
                .claim("role", "SERVICE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
