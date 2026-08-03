package com.john.northgate.toll.dto;

import java.time.OffsetDateTime;

public record ExceptionResponseDto(
        Long id,
        String plate,
        String type,
        String description,
        String status,
        OffsetDateTime createdAt
) {
}
