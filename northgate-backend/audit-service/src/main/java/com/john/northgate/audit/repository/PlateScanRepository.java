package com.john.northgate.audit.repository;

import com.john.northgate.audit.document.PlateScan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PlateScanRepository extends MongoRepository<PlateScan, String> {

    Optional<PlateScan> findFirstByLaneNumberOrderByScannedAtDesc(Integer laneNumber);
}
