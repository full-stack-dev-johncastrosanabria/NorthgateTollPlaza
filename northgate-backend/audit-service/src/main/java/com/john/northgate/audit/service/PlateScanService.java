package com.john.northgate.audit.service;

import com.john.northgate.audit.document.PlateScan;
import com.john.northgate.audit.dto.PlateScanRequestDto;
import com.john.northgate.audit.dto.PlateScanResponseDto;
import com.john.northgate.audit.exception.ResourceNotFoundException;
import com.john.northgate.audit.repository.PlateScanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PlateScanService {

    private final PlateScanRepository plateScanRepository;

    public PlateScanResponseDto record(PlateScanRequestDto request) {
        PlateScan scan = new PlateScan();
        scan.setLaneNumber(request.laneNumber());
        scan.setPlate(request.plate().trim().toUpperCase());
        scan.setConfidence(request.confidence());
        scan.setTagId(request.tagId());
        scan.setScannedAt(Instant.now());
        return toDto(plateScanRepository.save(scan));
    }

    public PlateScanResponseDto latestForLane(Integer laneNumber) {
        return plateScanRepository.findFirstByLaneNumberOrderByScannedAtDesc(laneNumber)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("No recent scan for lane " + laneNumber));
    }

    private PlateScanResponseDto toDto(PlateScan s) {
        return new PlateScanResponseDto(
                s.getId(), s.getLaneNumber(), s.getPlate(), s.getConfidence(), s.getTagId(), s.getScannedAt());
    }
}
