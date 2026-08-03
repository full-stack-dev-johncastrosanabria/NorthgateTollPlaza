package com.john.northgate.toll.controllers;

import com.john.northgate.toll.dto.ShiftSummaryDto;
import com.john.northgate.toll.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/toll/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping("/current")
    public ResponseEntity<ShiftSummaryDto> current(Principal principal) {
        return ResponseEntity.ok(shiftService.currentShift(principal.getName()));
    }
}
