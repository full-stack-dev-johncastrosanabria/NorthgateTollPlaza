package com.john.northgate.toll.service;

import com.john.northgate.toll.dto.DashboardResponseDto;
import com.john.northgate.toll.dto.LaneStatsDto;
import com.john.northgate.toll.dto.TrafficBucketDto;
import com.john.northgate.toll.entity.ExceptionStatus;
import com.john.northgate.toll.entity.Lane;
import com.john.northgate.toll.entity.LaneMode;
import com.john.northgate.toll.entity.LaneStatus;
import com.john.northgate.toll.entity.Shift;
import com.john.northgate.toll.entity.ShiftStatus;
import com.john.northgate.toll.repository.LaneExceptionRepository;
import com.john.northgate.toll.repository.LaneRepository;
import com.john.northgate.toll.repository.PassRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.projection.LaneOpenExceptions;
import com.john.northgate.toll.repository.projection.LaneTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LaneRepository laneRepository;
    private final ShiftRepository shiftRepository;
    private final PassRepository passRepository;
    private final LaneExceptionRepository laneExceptionRepository;

    @Transactional(readOnly = true)
    public DashboardResponseDto overview() {
        OffsetDateTime dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        Map<Integer, LaneTotals> totals = passRepository.laneTotals(dayStart).stream()
                .collect(Collectors.toMap(LaneTotals::getLaneNumber, Function.identity()));

        Map<Integer, Long> openExceptions = laneExceptionRepository.openExceptionCounts().stream()
                .collect(Collectors.toMap(LaneOpenExceptions::getLaneNumber, LaneOpenExceptions::getOpenCount));

        Map<Integer, String> operators = shiftRepository.findByStatus(ShiftStatus.ACTIVE).stream()
                .collect(Collectors.toMap(
                        shift -> shift.getLane().getLaneNumber(),
                        shift -> shift.getStaff().getFullName(),
                        (a, b) -> a));

        List<Lane> lanes = laneRepository.findAllByOrderByLaneNumberAsc();

        List<LaneStatsDto> laneStats = lanes.stream()
                .map(lane -> toLaneStats(lane, totals, openExceptions, operators))
                .toList();

        List<TrafficBucketDto> traffic = passRepository.trafficByHour(dayStart).stream()
                .map(bucket -> new TrafficBucketDto(bucket.getHour(), bucket.getVehicles()))
                .toList();

        int lanesOpen = (int) lanes.stream().filter(l -> l.getStatus() == LaneStatus.OPEN).count();
        int queued = lanes.stream().mapToInt(Lane::getQueueLength).sum();

        return new DashboardResponseDto(
                passRepository.revenueSince(dayStart),
                passRepository.countByCreatedAtGreaterThanEqual(dayStart),
                lanesOpen,
                lanes.size(),
                queued,
                laneExceptionRepository.countByStatus(ExceptionStatus.OPEN),
                laneStats,
                traffic);
    }

    private LaneStatsDto toLaneStats(Lane lane,
                                     Map<Integer, LaneTotals> totals,
                                     Map<Integer, Long> openExceptions,
                                     Map<Integer, String> operators) {
        Integer number = lane.getLaneNumber();
        LaneTotals laneTotals = totals.get(number);

        return new LaneStatsDto(
                number,
                lane.getStatus().name(),
                lane.getMode().name(),
                lane.getMode() == LaneMode.AUTOMATED ? null : operators.get(number),
                lane.getQueueLength(),
                laneTotals == null ? 0L : laneTotals.getVehicles(),
                laneTotals == null ? BigDecimal.ZERO : laneTotals.getRevenue(),
                openExceptions.getOrDefault(number, 0L));
    }
}
