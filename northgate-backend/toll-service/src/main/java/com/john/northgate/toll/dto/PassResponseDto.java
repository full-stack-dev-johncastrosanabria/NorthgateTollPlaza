package com.john.northgate.toll.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PassResponseDto(
        Long id,
        String plate,
        String vehicleClassCode,
        String vehicleClassLabel,
        String paymentMethod,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
