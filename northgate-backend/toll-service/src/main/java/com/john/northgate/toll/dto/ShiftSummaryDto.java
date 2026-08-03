package com.john.northgate.toll.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ShiftSummaryDto(
        Long shiftId,
        Integer laneNumber,
        String operatorName,
        String staffCode,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        long vehicles,
        BigDecimal collected,
        long openExceptions
) {
}
