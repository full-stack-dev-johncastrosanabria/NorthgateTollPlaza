package com.john.northgate.audit.repository;

import com.john.northgate.audit.document.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    List<AuditEvent> findByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditEvent> findByLaneNumberOrderByOccurredAtDesc(Integer laneNumber, Pageable pageable);

    List<AuditEvent> findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(String staffCode, Pageable pageable);

    List<AuditEvent> findByEventTypeOrderByOccurredAtDesc(String eventType, Pageable pageable);
}
