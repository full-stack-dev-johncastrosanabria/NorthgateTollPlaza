package com.john.northgate.toll.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponseDto(
        BigDecimal revenueToday,
        long vehiclesToday,
        int lanesOpen,
        int lanesTotal,
        int vehiclesQueued,
        long exceptionsAwaitingReview,
        List<LaneStatsDto> lanes,
        List<TrafficBucketDto> trafficByHour
) {
}
