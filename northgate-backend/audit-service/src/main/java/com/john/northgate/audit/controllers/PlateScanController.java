package com.john.northgate.audit.controllers;

import com.john.northgate.audit.dto.PlateScanRequestDto;
import com.john.northgate.audit.dto.PlateScanResponseDto;
import com.john.northgate.audit.service.PlateScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit/scans")
@RequiredArgsConstructor
public class PlateScanController {

    private final PlateScanService plateScanService;

    @PostMapping
    public ResponseEntity<PlateScanResponseDto> record(@Valid @RequestBody PlateScanRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plateScanService.record(request));
    }

    @GetMapping("/latest")
    public ResponseEntity<PlateScanResponseDto> latest(@RequestParam Integer laneNumber) {
        return ResponseEntity.ok(plateScanService.latestForLane(laneNumber));
    }
}
