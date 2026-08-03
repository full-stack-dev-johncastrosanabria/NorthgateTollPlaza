package com.john.northgate.toll.service;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.dto.ExceptionResponseDto;
import com.john.northgate.toll.entity.ExceptionStatus;
import com.john.northgate.toll.entity.LaneException;
import com.john.northgate.toll.entity.Shift;
import com.john.northgate.toll.entity.ShiftStatus;
import com.john.northgate.toll.exception.NoActiveShiftException;
import com.john.northgate.toll.exception.ResourceNotFoundException;
import com.john.northgate.toll.repository.LaneExceptionRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LaneExceptionService {

    private final LaneExceptionRepository laneExceptionRepository;
    private final ShiftRepository shiftRepository;
    private final StaffRepository staffRepository;
    private final AuditClient auditClient;

    @Transactional(readOnly = true)
    public List<ExceptionResponseDto> forCurrentLane(String staffCode) {
        Shift shift = activeShift(staffCode);
        return laneExceptionRepository
                .findByLaneLaneNumberOrderByCreatedAtDesc(shift.getLane().getLaneNumber())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ExceptionResponseDto override(Long id, String staffCode) {
        LaneException exception = laneExceptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found: " + id));

        if (exception.getStatus() != ExceptionStatus.OPEN) {
            throw new IllegalArgumentException("Exception " + id + " is already resolved");
        }

        var staff = staffRepository.findByStaffCodeIgnoreCase(staffCode)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffCode));

        exception.setStatus(ExceptionStatus.OVERRIDDEN);
        exception.setResolvedAt(OffsetDateTime.now());
        exception.setResolvedBy(staff);

        auditClient.publish("EXCEPTION_OVERRIDDEN", staffCode, exception.getLane().getLaneNumber(),
                "LANE_EXCEPTION", String.valueOf(exception.getId()),
                Map.of("type", exception.getType().name(),
                        "plate", exception.getPlate() == null ? "" : exception.getPlate()));

        return toDto(exception);
    }

    private Shift activeShift(String staffCode) {
        return shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus(staffCode, ShiftStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveShiftException("No active shift for staff " + staffCode));
    }

    private ExceptionResponseDto toDto(LaneException e) {
        return new ExceptionResponseDto(
                e.getId(), e.getPlate(), e.getType().name(), e.getDescription(),
                e.getStatus().name(), e.getCreatedAt());
    }
}
