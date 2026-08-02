package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.Lane;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LaneRepository extends JpaRepository<Lane, Long> {

    Optional<Lane> findByLaneNumber(Integer laneNumber);
}
