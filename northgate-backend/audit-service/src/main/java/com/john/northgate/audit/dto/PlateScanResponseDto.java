package com.john.northgate.audit.dto;

import java.time.Instant;

public record PlateScanResponseDto(
        String id,
        Integer laneNumber,
        String plate,
        Double confidence,
        String tagId,
        Instant scannedAt
) {
}
