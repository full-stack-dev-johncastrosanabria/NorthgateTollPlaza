package com.john.northgate.audit.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The gate in front of the ingest endpoint: toll-service presents a token
 * signed with the shared secret, and this filter is what turns it into the
 * SERVICE authority that @PreAuthorize checks.
 */
class JwtAuthFilterTest {

    private static final String SECRET = "a-shared-secret-of-at-least-32-bytes-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtService(SECRET));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("grants ROLE_SERVICE to a token signed with the shared secret")
    void grantsServiceRoleForSharedSecretToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = requestWith("Bearer " + token("toll-service", "SERVICE", 300));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("toll-service");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SERVICE");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("prefixes the role claim so MANAGER becomes ROLE_MANAGER")
    void prefixesRoleClaim() throws Exception {
        MockHttpServletRequest request = requestWith("Bearer " + token("MG-02", "MANAGER", 300));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    @DisplayName("leaves the request unauthenticated when the token is signed with another secret")
    void rejectsTokenFromAnotherSecret() throws Exception {
        SecretKey foreign = Keys.hmacShaKeyFor("a-totally-different-secret-32-bytes-xx".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("toll-service").claim("role", "SERVICE")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(foreign).compact();

        filter.doFilter(requestWith("Bearer " + forged), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("leaves the request unauthenticated when the token has expired")
    void rejectsExpiredToken() throws Exception {
        filter.doFilter(requestWith("Bearer " + token("toll-service", "SERVICE", -60)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("passes an unauthenticated request straight through with no header")
    void passesThroughWithoutHeader() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        // The filter never rejects; the security config decides what 403s.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("ignores an Authorization header that is not a Bearer token")
    void ignoresNonBearerHeader() throws Exception {
        filter.doFilter(requestWith("Basic dXNlcjpwYXNz"),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest requestWith(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }

    private static String token(String subject, String role, long secondsToExpiry) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(Instant.now().plusSeconds(secondsToExpiry)))
                .signWith(KEY)
                .compact();
    }
}
