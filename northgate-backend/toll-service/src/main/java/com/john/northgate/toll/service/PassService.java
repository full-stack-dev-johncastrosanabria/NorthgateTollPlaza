package com.john.northgate.toll.service;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.dto.PassRequestDto;
import com.john.northgate.toll.dto.PassResponseDto;
import com.john.northgate.toll.dto.VehicleClassDto;
import com.john.northgate.toll.entity.*;
import com.john.northgate.toll.exception.NoActiveShiftException;
import com.john.northgate.toll.exception.ResourceNotFoundException;
import com.john.northgate.toll.repository.PassRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.VehicleClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PassService {

    private final PassRepository passRepository;
    private final ShiftRepository shiftRepository;
    private final VehicleClassRepository vehicleClassRepository;
    private final AuditClient auditClient;

    @Transactional(readOnly = true)
    public List<VehicleClassDto> tariff() {
        return vehicleClassRepository.findAllByOrderBySortOrderAsc().stream()
                .map(vc -> new VehicleClassDto(vc.getCode(), vc.getLabel(), vc.getFare()))
                .toList();
    }

    /** The console shows the tail of the shift, not the whole of it. */
    private static final int RECENT_PASS_LIMIT = 25;

    @Transactional(readOnly = true)
    public List<PassResponseDto> recentPasses(String staffCode) {
        Shift shift = activeShift(staffCode);
        return passRepository.findRecentByShift(shift.getId(), PageRequest.of(0, RECENT_PASS_LIMIT))
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public PassResponseDto recordPass(String staffCode, PassRequestDto request) {
        Shift shift = activeShift(staffCode);

        VehicleClass vehicleClass = vehicleClassRepository.findByCode(request.vehicleClassCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unknown vehicle class: " + request.vehicleClassCode()));

        PaymentMethod paymentMethod = parsePaymentMethod(request.paymentMethod());

        Pass pass = new Pass();
        pass.setLane(shift.getLane());
        pass.setShift(shift);
        pass.setVehicleClass(vehicleClass);
        pass.setPlate(request.plate().trim().toUpperCase());
        pass.setPaymentMethod(paymentMethod);
        // Snapshot the fare so historical revenue survives tariff changes.
        pass.setAmount(vehicleClass.getFare());
        pass.setCreatedAt(OffsetDateTime.now());

        Pass saved = passRepository.save(pass);

        auditClient.publish("PASS_RECORDED", staffCode, shift.getLane().getLaneNumber(), "PASS",
                String.valueOf(saved.getId()),
                Map.of("plate", saved.getPlate(),
                        "vehicleClass", vehicleClass.getCode(),
                        "paymentMethod", paymentMethod.name(),
                        "amount", saved.getAmount().toPlainString()));

        return toDto(saved);
    }

    private Shift activeShift(String staffCode) {
        return shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus(staffCode, ShiftStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveShiftException(
                        "No active shift for staff " + staffCode));
    }

    private PaymentMethod parsePaymentMethod(String raw) {
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown payment method: " + raw);
        }
    }

    private PassResponseDto toDto(Pass pass) {
        return new PassResponseDto(
                pass.getId(),
                pass.getPlate(),
                pass.getVehicleClass().getCode(),
                pass.getVehicleClass().getLabel(),
                pass.getPaymentMethod().name(),
                pass.getAmount(),
                pass.getCreatedAt());
    }
}
