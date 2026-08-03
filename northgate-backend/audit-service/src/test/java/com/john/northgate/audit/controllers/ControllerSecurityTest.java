package com.john.northgate.audit.controllers;

import com.john.northgate.audit.dto.AuditEventRequestDto;
import com.john.northgate.audit.dto.AuditEventResponseDto;
import com.john.northgate.audit.service.AuditEventService;
import com.john.northgate.audit.service.PlateScanService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail is only worth anything if arbitrary callers cannot write to
 * it. Ingest is reserved for the service token toll-service presents; reading
 * back is for managers and services.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControllerSecurityTest {

    private static final String EVENT_JSON = """
            {"eventType":"PASS_RECORDED","staffCode":"OP-14","laneNumber":3,
             "entityType":"PASS","entityId":"42","payload":{"plate":"KTR 8891"}}""";

    @Autowired
    private MockMvc mockMvc;

    @Value("${northgate.jwt.secret}")
    private String secret;

    @MockitoBean
    private AuditEventService auditEventService;
    @MockitoBean
    private PlateScanService plateScanService;

    @Test
    @DisplayName("only the service token may write to the audit trail")
    void ingestIsServiceOnly() throws Exception {
        when(auditEventService.record(any(AuditEventRequestDto.class))).thenReturn(storedEvent());

        mockMvc.perform(postEvent(token("toll-service", "SERVICE")))
                .andExpect(status().isCreated());
        mockMvc.perform(postEvent(token("MG-02", "MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(postEvent(token("OP-14", "OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated caller cannot write to the audit trail")
    void ingestRefusesAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/audit/events")
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("managers and services may read the trail, operators may not")
    void queryIsForManagersAndServices() throws Exception {
        when(auditEventService.query(any(), any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/events").header("Authorization", bearer(token("MG-02", "MANAGER"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit/events").header("Authorization", bearer(token("toll-service", "SERVICE"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit/events").header("Authorization", bearer(token("OP-14", "OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token signed with another secret cannot reach the trail")
    void refusesTokenFromAnotherSecret() throws Exception {
        String forged = Jwts.builder().subject("toll-service").claim("role", "SERVICE")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor("a-totally-different-secret-32-bytes-xx".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(postEvent(forged)).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.RequestBuilder postEvent(String token) {
        return post("/api/audit/events")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVENT_JSON);
    }

    private static AuditEventResponseDto storedEvent() {
        return new AuditEventResponseDto("id1", "PASS_RECORDED", "OP-14", 3, "PASS", "42",
                Map.of("plate", "KTR 8891"), Instant.now());
    }

    private String token(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
