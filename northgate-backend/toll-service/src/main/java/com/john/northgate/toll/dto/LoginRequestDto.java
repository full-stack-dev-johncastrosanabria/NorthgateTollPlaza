package com.john.northgate.toll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequestDto(

        @NotBlank(message = "Staff ID is required")
        String staffCode,

        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{4}", message = "PIN must be 4 digits")
        String pin
) {
}
