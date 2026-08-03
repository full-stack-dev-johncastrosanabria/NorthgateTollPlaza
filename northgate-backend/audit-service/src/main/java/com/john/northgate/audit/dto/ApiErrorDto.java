package com.john.northgate.audit.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorDto(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
}
