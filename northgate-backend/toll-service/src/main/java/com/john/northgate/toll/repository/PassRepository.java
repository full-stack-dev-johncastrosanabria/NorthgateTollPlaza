package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.Pass;
import com.john.northgate.toll.repository.projection.HourBucket;
import com.john.northgate.toll.repository.projection.LaneTotals;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface PassRepository extends JpaRepository<Pass, Long> {

    @Query("""
            select p from Pass p
              join fetch p.vehicleClass
            where p.shift.id = :shiftId
            order by p.createdAt desc
            """)
    List<Pass> findRecentByShift(@Param("shiftId") Long shiftId, Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Pass p where p.shift.id = :shiftId")
    BigDecimal totalCollectedByShift(@Param("shiftId") Long shiftId);

    long countByShiftId(Long shiftId);

    @Query("select coalesce(sum(p.amount), 0) from Pass p where p.createdAt >= :dayStart")
    BigDecimal revenueSince(@Param("dayStart") OffsetDateTime dayStart);

    long countByCreatedAtGreaterThanEqual(OffsetDateTime dayStart);

    /** Per-lane totals for today, including lanes with no traffic at all. */
    @Query(value = """
            SELECT l.lane_number               AS "laneNumber",
                   COUNT(p.id)                 AS "vehicles",
                   COALESCE(SUM(p.amount), 0)  AS "revenue"
            FROM lane l
            LEFT JOIN pass p ON p.lane_id = l.id AND p.created_at >= :dayStart
            GROUP BY l.lane_number
            ORDER BY l.lane_number
            """, nativeQuery = true)
    List<LaneTotals> laneTotals(@Param("dayStart") OffsetDateTime dayStart);

    @Query(value = """
            SELECT EXTRACT(HOUR FROM created_at)::int AS "hour",
                   COUNT(*)                           AS "vehicles"
            FROM pass
            WHERE created_at >= :dayStart
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<HourBucket> trafficByHour(@Param("dayStart") OffsetDateTime dayStart);
}
