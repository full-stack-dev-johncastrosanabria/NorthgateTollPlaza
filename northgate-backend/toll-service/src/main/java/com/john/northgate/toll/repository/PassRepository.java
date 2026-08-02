package com.john.northgate.toll.repository;

import com.john.northgate.toll.entity.Pass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PassRepository extends JpaRepository<Pass, Long> {

    @Query("""
            select p from Pass p
              join fetch p.vehicleClass
            where p.shift.id = :shiftId
            order by p.createdAt desc
            """)
    List<Pass> findRecentByShift(@Param("shiftId") Long shiftId);

    @Query("select coalesce(sum(p.amount), 0) from Pass p where p.shift.id = :shiftId")
    BigDecimal totalCollectedByShift(@Param("shiftId") Long shiftId);

    long countByShiftId(Long shiftId);
}
