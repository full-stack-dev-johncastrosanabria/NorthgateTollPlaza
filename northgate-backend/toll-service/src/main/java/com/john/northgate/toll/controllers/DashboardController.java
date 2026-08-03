package com.john.northgate.toll.controllers;

import com.john.northgate.toll.dto.DashboardResponseDto;
import com.john.northgate.toll.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/toll/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DashboardResponseDto> overview() {
        return ResponseEntity.ok(dashboardService.overview());
    }
}
