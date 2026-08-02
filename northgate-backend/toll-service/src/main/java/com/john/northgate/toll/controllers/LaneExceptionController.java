package com.john.northgate.toll.controllers;

import com.john.northgate.toll.dto.ExceptionResponseDto;
import com.john.northgate.toll.service.LaneExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/toll/exceptions")
@RequiredArgsConstructor
public class LaneExceptionController {

    private final LaneExceptionService laneExceptionService;

    @GetMapping
    public ResponseEntity<List<ExceptionResponseDto>> forCurrentLane(Principal principal) {
        return ResponseEntity.ok(laneExceptionService.forCurrentLane(principal.getName()));
    }

    @PostMapping("/{id}/override")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<ExceptionResponseDto> override(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(laneExceptionService.override(id, principal.getName()));
    }
}
