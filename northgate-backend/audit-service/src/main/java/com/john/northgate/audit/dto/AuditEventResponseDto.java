package com.john.northgate.audit.dto;

import java.time.Instant;
import java.util.Map;

public record AuditEventResponseDto(
        String id,
        String eventType,
        String staffCode,
        Integer laneNumber,
        String entityType,
        String entityId,
        Map<String, Object> payload,
        Instant occurredAt
) {
}
