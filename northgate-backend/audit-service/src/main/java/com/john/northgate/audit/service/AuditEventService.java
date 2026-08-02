package com.john.northgate.audit.service;

import com.john.northgate.audit.document.AuditEvent;
import com.john.northgate.audit.dto.AuditEventRequestDto;
import com.john.northgate.audit.dto.AuditEventResponseDto;
import com.john.northgate.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventResponseDto record(AuditEventRequestDto request) {
        AuditEvent event = new AuditEvent();
        event.setEventType(request.eventType());
        event.setStaffCode(request.staffCode());
        event.setLaneNumber(request.laneNumber());
        event.setEntityType(request.entityType());
        event.setEntityId(request.entityId());
        event.setPayload(request.payload() == null ? Map.of() : request.payload());
        event.setOccurredAt(Instant.now());
        return toDto(auditEventRepository.save(event));
    }

    public List<AuditEventResponseDto> query(Integer laneNumber, String staffCode, String eventType, int limit) {
        Pageable page = PageRequest.of(0, limit);

        List<AuditEvent> events;
        if (laneNumber != null) {
            events = auditEventRepository.findByLaneNumberOrderByOccurredAtDesc(laneNumber, page);
        } else if (staffCode != null && !staffCode.isBlank()) {
            events = auditEventRepository.findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(staffCode, page);
        } else if (eventType != null && !eventType.isBlank()) {
            events = auditEventRepository.findByEventTypeOrderByOccurredAtDesc(eventType, page);
        } else {
            events = auditEventRepository.findByOrderByOccurredAtDesc(page);
        }
        return events.stream().map(this::toDto).toList();
    }

    private AuditEventResponseDto toDto(AuditEvent e) {
        return new AuditEventResponseDto(
                e.getId(), e.getEventType(), e.getStaffCode(), e.getLaneNumber(),
                e.getEntityType(), e.getEntityId(), e.getPayload(), e.getOccurredAt());
    }
}
