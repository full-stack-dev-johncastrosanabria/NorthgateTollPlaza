package com.john.northgate.toll.controllers;

import com.john.northgate.toll.dto.PassRequestDto;
import com.john.northgate.toll.dto.PassResponseDto;
import com.john.northgate.toll.service.PassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/toll/passes")
@RequiredArgsConstructor
public class PassController {

    private final PassService passService;

    @GetMapping
    public ResponseEntity<List<PassResponseDto>> recent(Principal principal) {
        return ResponseEntity.ok(passService.recentPasses(principal.getName()));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<PassResponseDto> record(@Valid @RequestBody PassRequestDto request,
                                                  Principal principal) {
        PassResponseDto created = passService.recordPass(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
