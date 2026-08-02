package com.john.northgate.toll.dto;

import java.math.BigDecimal;

public record LaneStatsDto(
        Integer laneNumber,
        String status,
        String mode,
        /** Operator on the active shift; null on unmanned automated lanes. */
        String operatorName,
        int queueLength,
        /** Per-lane count for today; these sum to the plaza's vehiclesToday. */
        long vehiclesToday,
        BigDecimal revenue,
        long openExceptions
) {
}
