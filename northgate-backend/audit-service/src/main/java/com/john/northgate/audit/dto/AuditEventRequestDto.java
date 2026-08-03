package com.john.northgate.audit.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AuditEventRequestDto(

        @NotBlank(message = "eventType is required")
        String eventType,

        String staffCode,
        Integer laneNumber,
        String entityType,
        String entityId,
        Map<String, Object> payload
) {
}
