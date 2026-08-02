package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.ExceptionStatus;
import com.john.northgate.toll.entity.LaneException;
import com.john.northgate.toll.repository.projection.LaneOpenExceptions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LaneExceptionRepository extends JpaRepository<LaneException, Long> {

    List<LaneException> findByLaneLaneNumberOrderByCreatedAtDesc(Integer laneNumber);

    long countByLaneLaneNumberAndStatus(Integer laneNumber, ExceptionStatus status);

    long countByStatus(ExceptionStatus status);

    /** Open-exception counts per lane, including lanes with none. */
    @Query(value = """
            SELECT l.lane_number AS "laneNumber",
                   COUNT(e.id)   AS "openCount"
            FROM lane l
            LEFT JOIN lane_exception e ON e.lane_id = l.id AND e.status = 'OPEN'
            GROUP BY l.lane_number
            ORDER BY l.lane_number
            """, nativeQuery = true)
    List<LaneOpenExceptions> openExceptionCounts();
}
