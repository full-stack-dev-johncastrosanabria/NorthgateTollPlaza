package com.john.northgate.audit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlateScanRequestDto(

        @NotNull(message = "laneNumber is required")
        Integer laneNumber,

        @NotBlank(message = "plate is required")
        String plate,

        @DecimalMin(value = "0.0", message = "confidence must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "confidence must be between 0 and 1")
        Double confidence,

        String tagId
) {
}
