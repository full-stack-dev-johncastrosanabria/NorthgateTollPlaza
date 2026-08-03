package com.john.northgate.toll.dto;

public record TrafficBucketDto(
        /** Hour of day, 0–23, in the plaza's local time. */
        int hour,
        long vehicles
) {
}
