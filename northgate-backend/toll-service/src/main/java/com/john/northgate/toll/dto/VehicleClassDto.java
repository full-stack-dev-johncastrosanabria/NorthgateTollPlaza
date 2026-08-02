package com.john.northgate.toll.dto;

import java.math.BigDecimal;

public record VehicleClassDto(
        String code,
        String label,
        BigDecimal fare
) {
}
