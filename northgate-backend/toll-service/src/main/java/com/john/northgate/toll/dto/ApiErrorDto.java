package com.john.northgate.toll.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorDto(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        /** Field-level validation failures; empty for non-validation errors. */
        Map<String, String> fieldErrors
) {
}
