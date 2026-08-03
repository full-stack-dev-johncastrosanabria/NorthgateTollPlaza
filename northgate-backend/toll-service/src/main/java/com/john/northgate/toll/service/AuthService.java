package com.john.northgate.toll.service;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.config.JwtService;
import com.john.northgate.toll.dto.LoginRequestDto;
import com.john.northgate.toll.dto.LoginResponseDto;
import com.john.northgate.toll.entity.Shift;
import com.john.northgate.toll.entity.ShiftStatus;
import com.john.northgate.toll.entity.Staff;
import com.john.northgate.toll.exception.InvalidCredentialsException;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepository staffRepository;
    private final ShiftRepository shiftRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditClient auditClient;

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        Staff staff = staffRepository.findByStaffCodeIgnoreCase(request.staffCode())
                .filter(Staff::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid staff ID or PIN"));

        if (!passwordEncoder.matches(request.pin(), staff.getPinHash())) {
            throw new InvalidCredentialsException("Invalid staff ID or PIN");
        }

        Integer laneNumber = shiftRepository
                .findByStaffStaffCodeIgnoreCaseAndStatus(staff.getStaffCode(), ShiftStatus.ACTIVE)
                .map(Shift::getLane)
                .map(lane -> lane.getLaneNumber())
                .orElse(null);

        String token = jwtService.issue(staff.getStaffCode(), staff.getFullName(), staff.getRole().name());

        auditClient.publish("SIGN_IN", staff.getStaffCode(), laneNumber, "STAFF",
                String.valueOf(staff.getId()), Map.of("role", staff.getRole().name()));

        return new LoginResponseDto(
                token, staff.getStaffCode(), staff.getFullName(), staff.getRole().name(), laneNumber);
    }
}
