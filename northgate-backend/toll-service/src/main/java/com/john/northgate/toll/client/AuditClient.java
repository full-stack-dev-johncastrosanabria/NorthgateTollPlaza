package com.john.northgate.toll.client;

import com.john.northgate.toll.config.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Fire-and-forget publisher to audit-service. Audit must never block or fail
 * a toll transaction, so errors are logged and dropped by design.
 */
@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    private final RestClient restClient;
    private final JwtService jwtService;

    public AuditClient(@Value("${northgate.audit.base-url}") String baseUrl, JwtService jwtService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.jwtService = jwtService;
    }

    @Async
    public void publish(String eventType, String staffCode, Integer laneNumber,
                        String entityType, String entityId, Map<String, Object> payload) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("eventType", eventType);
            body.put("staffCode", staffCode);
            body.put("laneNumber", laneNumber);
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            body.put("payload", payload);

            restClient.post()
                    .uri("/api/audit/events")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Audit event dropped: {} ({})", eventType, e.getMessage());
        }
    }
}
