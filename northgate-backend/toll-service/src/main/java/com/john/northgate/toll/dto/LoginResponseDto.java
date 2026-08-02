package com.john.northgate.toll.dto;

public record LoginResponseDto(
        String token,
        String staffCode,
        String fullName,
        String role,
        /** Lane the operator is on shift at; null for managers. */
        Integer laneNumber
) {
}
