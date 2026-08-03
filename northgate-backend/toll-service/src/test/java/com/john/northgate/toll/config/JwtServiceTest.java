package com.john.northgate.toll.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real signing and verification — no mocks. These are the tokens both
 * services trust, so the crypto itself is what needs exercising.
 */
class JwtServiceTest {

    private static final String SECRET = "a-test-signing-secret-of-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET, 480);

    @Test
    @DisplayName("round-trips the staff code, name and role through a signed token")
    void roundTripsOperatorClaims() {
        String token = jwtService.issue("OP-14", "R. Alvarez", "OPERATOR");

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("OP-14");
        assertThat(claims.get("name", String.class)).isEqualTo("R. Alvarez");
        assertThat(claims.get("role", String.class)).isEqualTo("OPERATOR");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("marks the service token with the SERVICE role")
    void issuesServiceTokenWithServiceRole() {
        Claims claims = jwtService.parse(jwtService.issueServiceToken());

        assertThat(claims.getSubject()).isEqualTo("toll-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService impostor = new JwtService("a-completely-different-secret-32-bytes-x", 480);
        String forged = impostor.issue("MG-02", "Not Really", "MANAGER");

        assertThatThrownBy(() -> jwtService.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects a token whose payload has been tampered with")
    void rejectsTamperedToken() {
        String token = jwtService.issue("OP-14", "R. Alvarez", "OPERATOR");
        String[] parts = token.split("\\.");
        // Keep the header and signature, swap in a different body.
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects an already-expired token")
    void rejectsExpiredToken() {
        JwtService alreadyExpired = new JwtService(SECRET, -1);
        String token = alreadyExpired.issue("OP-14", "R. Alvarez", "OPERATOR");

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }
}
