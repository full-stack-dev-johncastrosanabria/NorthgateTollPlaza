package com.john.northgate.toll.service;

import com.john.northgate.toll.dto.ShiftSummaryDto;
import com.john.northgate.toll.entity.ExceptionStatus;
import com.john.northgate.toll.entity.Shift;
import com.john.northgate.toll.entity.ShiftStatus;
import com.john.northgate.toll.exception.NoActiveShiftException;
import com.john.northgate.toll.repository.LaneExceptionRepository;
import com.john.northgate.toll.repository.PassRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final PassRepository passRepository;
    private final LaneExceptionRepository laneExceptionRepository;

    @Transactional(readOnly = true)
    public ShiftSummaryDto currentShift(String staffCode) {
        Shift shift = shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus(staffCode, ShiftStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveShiftException("No active shift for staff " + staffCode));

        Integer laneNumber = shift.getLane().getLaneNumber();

        return new ShiftSummaryDto(
                shift.getId(),
                laneNumber,
                shift.getStaff().getFullName(),
                shift.getStaff().getStaffCode(),
                shift.getStartsAt(),
                shift.getEndsAt(),
                passRepository.countByShiftId(shift.getId()),
                passRepository.totalCollectedByShift(shift.getId()),
                laneExceptionRepository.countByLaneLaneNumberAndStatus(laneNumber, ExceptionStatus.OPEN));
    }
}
