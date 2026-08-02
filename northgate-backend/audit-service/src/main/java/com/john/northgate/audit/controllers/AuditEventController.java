package com.john.northgate.audit.controllers;

import com.john.northgate.audit.dto.AuditEventRequestDto;
import com.john.northgate.audit.dto.AuditEventResponseDto;
import com.john.northgate.audit.service.AuditEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit/events")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEventService;

    /** Ingest endpoint — called only by toll-service with a service token. */
    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<AuditEventResponseDto> record(@Valid @RequestBody AuditEventRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditEventService.record(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','SERVICE')")
    public ResponseEntity<List<AuditEventResponseDto>> query(
            @RequestParam(required = false) Integer laneNumber,
            @RequestParam(required = false) String staffCode,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(auditEventService.query(laneNumber, staffCode, eventType, Math.min(limit, 200)));
    }
}
