package com.john.northgate.toll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassRequestDto(

        @NotBlank(message = "Vehicle class is required")
        String vehicleClassCode,

        @NotBlank(message = "Plate is required")
        @Size(max = 15, message = "Plate must be at most 15 characters")
        String plate,

        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {
}
