package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.*;
import com.john.northgate.toll.repository.projection.HourBucket;
import com.john.northgate.toll.repository.projection.LaneOpenExceptions;
import com.john.northgate.toll.repository.projection.LaneTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the dashboard's native SQL against a real PostgreSQL, because the
 * queries use Postgres-specific syntax (EXTRACT ... ::int) and outer-join
 * semantics that an in-memory database would not reproduce faithfully.
 *
 * <p>Requires a local database created once with:
 * <pre>createdb northgate_toll_test</pre>
 * Flyway stops at V1 so only the schema is applied — the V2/V3 demo seed would
 * drown the fixtures below. Each test runs in a transaction that is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/northgate_toll_test",
        "spring.flyway.target=1",
        "spring.jpa.hibernate.ddl-auto=validate",
})
class DashboardAggregationTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private PassRepository passRepository;
    @Autowired
    private LaneExceptionRepository laneExceptionRepository;

    private OffsetDateTime dayStart;
    private Lane lane1;
    private Lane lane2;
    private Lane idleLane;
    private VehicleClass car;
    private VehicleClass truck;

    @BeforeEach
    void setUp() {
        dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        lane1 = persistLane(1, LaneMode.MANNED, LaneStatus.OPEN);
        lane2 = persistLane(2, LaneMode.MANNED, LaneStatus.OPEN);
        idleLane = persistLane(6, LaneMode.AUTOMATED, LaneStatus.CLOSED);

        car = persistVehicleClass("CAR", "Car", "3.00", 1);
        truck = persistVehicleClass("TRUCK", "Truck", "14.00", 2);
    }

    @Test
    @DisplayName("counts vehicles and sums revenue per lane")
    void aggregatesPerLaneTotals() {
        persistPass(lane1, car, todayAt(7, 15));
        persistPass(lane1, truck, todayAt(7, 45));
        persistPass(lane2, car, todayAt(9, 10));
        entityManager.flush();

        Map<Integer, LaneTotals> totals = laneTotals();

        assertThat(totals.get(1).getVehicles()).isEqualTo(2L);
        assertThat(totals.get(1).getRevenue()).isEqualByComparingTo("17.00");
        assertThat(totals.get(2).getVehicles()).isEqualTo(1L);
        assertThat(totals.get(2).getRevenue()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("keeps a lane with no passes in the result instead of dropping it")
    void keepsIdleLaneInLaneTotals() {
        persistPass(lane1, car, todayAt(7, 15));
        entityManager.flush();

        Map<Integer, LaneTotals> totals = laneTotals();

        // The LEFT JOIN is what puts lane 6 here at all; an inner join would omit it
        // and the lane would silently vanish from the manager's grid.
        assertThat(totals).containsKey(idleLane.getLaneNumber());
        assertThat(totals.get(6).getVehicles()).isZero();
        assertThat(totals.get(6).getRevenue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("excludes passes recorded before today from the per-lane totals")
    void excludesYesterdayFromLaneTotals() {
        persistPass(lane1, car, todayAt(8, 0));
        persistPass(lane1, truck, yesterdayAt(23, 30));
        entityManager.flush();

        Map<Integer, LaneTotals> totals = laneTotals();

        assertThat(totals.get(1).getVehicles()).isEqualTo(1L);
        assertThat(totals.get(1).getRevenue()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("per-lane vehicle counts sum to the plaza total")
    void laneCountsReconcileWithPlazaTotal() {
        persistPass(lane1, car, todayAt(7, 15));
        persistPass(lane1, truck, todayAt(7, 45));
        persistPass(lane2, car, todayAt(9, 10));
        persistPass(lane1, car, yesterdayAt(22, 0));
        entityManager.flush();

        long plazaVehicles = passRepository.countByCreatedAtGreaterThanEqual(dayStart);
        long summedAcrossLanes = laneTotals().values().stream()
                .mapToLong(LaneTotals::getVehicles)
                .sum();

        // The dashboard shows both figures at once, so they have to agree.
        assertThat(summedAcrossLanes).isEqualTo(plazaVehicles).isEqualTo(3L);
        assertThat(passRepository.revenueSince(dayStart)).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("buckets traffic by hour of day, ascending, today only")
    void bucketsTrafficByHour() {
        persistPass(lane1, car, todayAt(7, 5));
        persistPass(lane2, car, todayAt(7, 55));
        persistPass(lane1, truck, todayAt(9, 30));
        persistPass(lane1, car, yesterdayAt(7, 30));
        entityManager.flush();

        List<HourBucket> buckets = passRepository.trafficByHour(dayStart);

        assertThat(buckets)
                .extracting(b -> b.getHour() + "=" + b.getVehicles())
                .containsExactly("7=2", "9=1");
    }

    @Test
    @DisplayName("counts only OPEN exceptions and reports zero for clean lanes")
    void countsOnlyOpenExceptionsPerLane() {
        persistException(lane1, ExceptionType.VIOLATION, ExceptionStatus.OPEN);
        persistException(lane1, ExceptionType.UNREAD_TAG, ExceptionStatus.OPEN);
        persistException(lane1, ExceptionType.OVERPAYMENT, ExceptionStatus.CLEARED);
        persistException(lane2, ExceptionType.VIOLATION, ExceptionStatus.OVERRIDDEN);
        entityManager.flush();

        Map<Integer, Long> counts = laneExceptionRepository.openExceptionCounts().stream()
                .collect(Collectors.toMap(LaneOpenExceptions::getLaneNumber, LaneOpenExceptions::getOpenCount));

        assertThat(counts.get(1)).isEqualTo(2L);
        assertThat(counts.get(2)).isZero();
        assertThat(counts.get(6)).isZero();
        assertThat(laneExceptionRepository.countByStatus(ExceptionStatus.OPEN)).isEqualTo(2L);
    }

    private Map<Integer, LaneTotals> laneTotals() {
        return passRepository.laneTotals(dayStart).stream()
                .collect(Collectors.toMap(LaneTotals::getLaneNumber, Function.identity()));
    }

    private OffsetDateTime todayAt(int hour, int minute) {
        return LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private OffsetDateTime yesterdayAt(int hour, int minute) {
        return LocalDate.now().minusDays(1).atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private Lane persistLane(int number, LaneMode mode, LaneStatus status) {
        Lane lane = new Lane();
        lane.setLaneNumber(number);
        lane.setMode(mode);
        lane.setStatus(status);
        lane.setQueueLength(0);
        return entityManager.persist(lane);
    }

    private VehicleClass persistVehicleClass(String code, String label, String fare, int sortOrder) {
        VehicleClass vehicleClass = new VehicleClass();
        vehicleClass.setCode(code);
        vehicleClass.setLabel(label);
        vehicleClass.setFare(new BigDecimal(fare));
        vehicleClass.setSortOrder(sortOrder);
        return entityManager.persist(vehicleClass);
    }

    private void persistPass(Lane lane, VehicleClass vehicleClass, OffsetDateTime at) {
        Pass pass = new Pass();
        pass.setLane(lane);
        pass.setVehicleClass(vehicleClass);
        pass.setPlate("TST 0001");
        pass.setPaymentMethod(PaymentMethod.CASH);
        pass.setAmount(vehicleClass.getFare());
        pass.setCreatedAt(at);
        entityManager.persist(pass);
    }

    private void persistException(Lane lane, ExceptionType type, ExceptionStatus status) {
        LaneException exception = new LaneException();
        exception.setLane(lane);
        exception.setPlate("TST 0002");
        exception.setType(type);
        exception.setDescription("fixture");
        exception.setStatus(status);
        exception.setCreatedAt(todayAt(9, 0));
        entityManager.persist(exception);
    }
}
