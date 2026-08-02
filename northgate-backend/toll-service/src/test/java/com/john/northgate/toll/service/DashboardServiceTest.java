package com.john.northgate.toll.service;

import com.john.northgate.toll.dto.DashboardResponseDto;
import com.john.northgate.toll.dto.LaneStatsDto;
import com.john.northgate.toll.entity.*;
import com.john.northgate.toll.repository.LaneExceptionRepository;
import com.john.northgate.toll.repository.LaneRepository;
import com.john.northgate.toll.repository.PassRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.projection.HourBucket;
import com.john.northgate.toll.repository.projection.LaneOpenExceptions;
import com.john.northgate.toll.repository.projection.LaneTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers how the plaza overview is assembled from the repository aggregates.
 * The SQL itself is not exercised here — these are pure mapping tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private LaneRepository laneRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private PassRepository passRepository;
    @Mock
    private LaneExceptionRepository laneExceptionRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Lane lane1;
    private Lane lane5;
    private Lane lane6;

    @BeforeEach
    void setUp() {
        lane1 = lane(1, LaneMode.MANNED, LaneStatus.OPEN, 2);
        lane5 = lane(5, LaneMode.AUTOMATED, LaneStatus.OPEN, 1);
        lane6 = lane(6, LaneMode.AUTOMATED, LaneStatus.CLOSED, 0);

        when(laneRepository.findAllByOrderByLaneNumberAsc()).thenReturn(List.of(lane1, lane5, lane6));
        when(passRepository.laneTotals(any(OffsetDateTime.class))).thenReturn(List.of(
                new Totals(1, 390L, new BigDecimal("2137.00")),
                new Totals(5, 391L, new BigDecimal("1627.50"))));
        when(passRepository.trafficByHour(any(OffsetDateTime.class))).thenReturn(List.of(
                new Bucket(5, 88L), new Bucket(6, 176L)));
        when(passRepository.revenueSince(any(OffsetDateTime.class))).thenReturn(new BigDecimal("3764.50"));
        when(passRepository.countByCreatedAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(781L);
        when(laneExceptionRepository.openExceptionCounts()).thenReturn(List.of(new OpenExc(1, 4L)));
        when(laneExceptionRepository.countByStatus(ExceptionStatus.OPEN)).thenReturn(4L);
        // Lane 5 carries a stray active shift on purpose: nothing in the schema
        // stops one being opened on an automated lane, and the overview must
        // still report it as unmanned.
        when(shiftRepository.findByStatus(ShiftStatus.ACTIVE)).thenReturn(List.of(
                shiftOn(lane1, "OP-07", "M. Iqbal"),
                shiftOn(lane5, "OP-99", "Should Not Appear")));
    }

    @Test
    @DisplayName("reports the plaza headline figures from the repositories")
    void reportsPlazaHeadlineFigures() {
        DashboardResponseDto overview = dashboardService.overview();

        assertThat(overview.revenueToday()).isEqualByComparingTo("3764.50");
        assertThat(overview.vehiclesToday()).isEqualTo(781L);
        assertThat(overview.lanesTotal()).isEqualTo(3);
        assertThat(overview.exceptionsAwaitingReview()).isEqualTo(4L);
    }

    @Test
    @DisplayName("counts only OPEN lanes as open and sums the queue across every lane")
    void countsOpenLanesAndTotalQueue() {
        DashboardResponseDto overview = dashboardService.overview();

        // Lanes 1 and 5 are OPEN; lane 6 is CLOSED but its queue still counts.
        assertThat(overview.lanesOpen()).isEqualTo(2);
        assertThat(overview.vehiclesQueued()).isEqualTo(3);
    }

    @Test
    @DisplayName("keeps a lane with no traffic in the grid instead of dropping it")
    void keepsIdleLaneInTheGrid() {
        DashboardResponseDto overview = dashboardService.overview();

        // Lane 6 has no row in laneTotals at all.
        LaneStatsDto idle = laneStats(overview, 6);
        assertThat(idle.vehiclesToday()).isZero();
        assertThat(idle.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(idle.status()).isEqualTo("CLOSED");
        assertThat(overview.lanes()).hasSize(3);
    }

    @Test
    @DisplayName("names the operator on a manned lane's active shift")
    void namesOperatorFromActiveShift() {
        LaneStatsDto manned = laneStats(dashboardService.overview(), 1);

        assertThat(manned.operatorName()).isEqualTo("M. Iqbal");
        assertThat(manned.mode()).isEqualTo("MANNED");
        assertThat(manned.vehiclesToday()).isEqualTo(390L);
        assertThat(manned.revenue()).isEqualByComparingTo("2137.00");
    }

    @Test
    @DisplayName("leaves the operator empty on automated lanes")
    void reportsAutomatedLanesAsUnmanned() {
        DashboardResponseDto overview = dashboardService.overview();

        assertThat(laneStats(overview, 5).operatorName()).isNull();
        assertThat(laneStats(overview, 6).operatorName()).isNull();
    }

    @Test
    @DisplayName("defaults a lane's open-exception count to zero when it has none")
    void defaultsOpenExceptionsToZero() {
        DashboardResponseDto overview = dashboardService.overview();

        assertThat(laneStats(overview, 1).openExceptions()).isEqualTo(4L);
        assertThat(laneStats(overview, 5).openExceptions()).isZero();
    }

    @Test
    @DisplayName("maps the hourly traffic buckets in order")
    void mapsHourlyTraffic() {
        DashboardResponseDto overview = dashboardService.overview();

        assertThat(overview.trafficByHour())
                .extracting(bucket -> bucket.hour() + ":" + bucket.vehicles())
                .containsExactly("5:88", "6:176");
    }

    private static LaneStatsDto laneStats(DashboardResponseDto overview, int laneNumber) {
        return overview.lanes().stream()
                .filter(l -> l.laneNumber() == laneNumber)
                .findFirst()
                .orElseThrow(() -> new AssertionError("lane " + laneNumber + " missing from overview"));
    }

    private static Lane lane(int number, LaneMode mode, LaneStatus status, int queueLength) {
        Lane lane = new Lane();
        lane.setLaneNumber(number);
        lane.setMode(mode);
        lane.setStatus(status);
        lane.setQueueLength(queueLength);
        return lane;
    }

    private static Shift shiftOn(Lane lane, String staffCode, String fullName) {
        Staff staff = new Staff();
        staff.setStaffCode(staffCode);
        staff.setFullName(fullName);

        Shift shift = new Shift();
        shift.setLane(lane);
        shift.setStaff(staff);
        shift.setStatus(ShiftStatus.ACTIVE);
        return shift;
    }

    private record Totals(Integer number, Long count, BigDecimal money) implements LaneTotals {
        @Override
        public Integer getLaneNumber() {
            return number;
        }

        @Override
        public Long getVehicles() {
            return count;
        }

        @Override
        public BigDecimal getRevenue() {
            return money;
        }
    }

    private record OpenExc(Integer number, Long count) implements LaneOpenExceptions {
        @Override
        public Integer getLaneNumber() {
            return number;
        }

        @Override
        public Long getOpenCount() {
            return count;
        }
    }

    private record Bucket(Integer slot, Long count) implements HourBucket {
        @Override
        public Integer getHour() {
            return slot;
        }

        @Override
        public Long getVehicles() {
            return count;
        }
    }
}
