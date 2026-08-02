package com.john.northgate.toll.controllers;

import com.john.northgate.toll.dto.VehicleClassDto;
import com.john.northgate.toll.service.PassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/toll/vehicle-classes")
@RequiredArgsConstructor
public class TariffController {

    private final PassService passService;

    @GetMapping
    public ResponseEntity<List<VehicleClassDto>> tariff() {
        return ResponseEntity.ok(passService.tariff());
    }
}
