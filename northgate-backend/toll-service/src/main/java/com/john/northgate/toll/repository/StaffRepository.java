package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByStaffCodeIgnoreCase(String staffCode);
}
