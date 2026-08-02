package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.Shift;
import com.john.northgate.toll.entity.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByStaffStaffCodeIgnoreCaseAndStatus(String staffCode, ShiftStatus status);

    Optional<Shift> findByLaneLaneNumberAndStatus(Integer laneNumber, ShiftStatus status);
}
