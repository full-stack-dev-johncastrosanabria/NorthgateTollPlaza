package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.ExceptionStatus;
import com.john.northgate.toll.entity.LaneException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LaneExceptionRepository extends JpaRepository<LaneException, Long> {

    List<LaneException> findByLaneLaneNumberOrderByCreatedAtDesc(Integer laneNumber);

    long countByLaneLaneNumberAndStatus(Integer laneNumber, ExceptionStatus status);
}
