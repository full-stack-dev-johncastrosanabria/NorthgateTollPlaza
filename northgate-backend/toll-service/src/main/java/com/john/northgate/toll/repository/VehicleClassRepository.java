package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.VehicleClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleClassRepository extends JpaRepository<VehicleClass, Long> {

    Optional<VehicleClass> findByCode(String code);

    List<VehicleClass> findAllByOrderBySortOrderAsc();
}
