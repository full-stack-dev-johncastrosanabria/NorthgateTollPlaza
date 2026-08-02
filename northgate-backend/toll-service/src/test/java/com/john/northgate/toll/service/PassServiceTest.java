package com.john.northgate.toll.service;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.dto.PassRequestDto;
import com.john.northgate.toll.dto.PassResponseDto;
import com.john.northgate.toll.entity.*;
import com.john.northgate.toll.exception.NoActiveShiftException;
import com.john.northgate.toll.exception.ResourceNotFoundException;
import com.john.northgate.toll.repository.PassRepository;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.VehicleClassRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassServiceTest {

    @Mock
    private PassRepository passRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private VehicleClassRepository vehicleClassRepository;
    @Mock
    private AuditClient auditClient;

    @InjectMocks
    private PassService passService;

    @Test
    @DisplayName("charges the tariff fare and snapshots it onto the pass")
    void snapshotsTheTariffFareOntoThePass() {
        givenActiveShiftOnLane(3);
        givenVehicleClass("TRUCK", "Truck", "14.00");
        givenSaveAssignsId(42L);

        PassResponseDto recorded = passService.recordPass("OP-14",
                new PassRequestDto("TRUCK", "LMD 2210", "CARD"));

        Pass saved = capturedPass();
        // The amount is copied from the tariff, not referenced, so a later rate
        // change cannot rewrite what this driver was charged.
        assertThat(saved.getAmount()).isEqualByComparingTo("14.00");
        assertThat(recorded.amount()).isEqualByComparingTo("14.00");
        assertThat(recorded.vehicleClassLabel()).isEqualTo("Truck");
    }

    @Test
    @DisplayName("normalises the plate and accepts a lower-case payment method")
    void normalisesPlateAndPaymentMethod() {
        givenActiveShiftOnLane(3);
        givenVehicleClass("CAR", "Car", "3.00");
        givenSaveAssignsId(43L);

        passService.recordPass("OP-14", new PassRequestDto("CAR", "  zzz 1234 ", "tag"));

        Pass saved = capturedPass();
        assertThat(saved.getPlate()).isEqualTo("ZZZ 1234");
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.TAG);
    }

    @Test
    @DisplayName("books the pass against the operator's own lane and shift")
    void recordsAgainstTheOperatorsLaneAndShift() {
        Shift shift = givenActiveShiftOnLane(5);
        givenVehicleClass("CAR", "Car", "3.00");
        givenSaveAssignsId(44L);

        passService.recordPass("OP-14", new PassRequestDto("CAR", "KTR 8891", "CASH"));

        Pass saved = capturedPass();
        assertThat(saved.getShift()).isSameAs(shift);
        assertThat(saved.getLane().getLaneNumber()).isEqualTo(5);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishes a PASS_RECORDED audit event carrying what was charged")
    void publishesPassRecordedAuditEvent() {
        givenActiveShiftOnLane(3);
        givenVehicleClass("VAN_SUV", "Van / SUV", "4.50");
        givenSaveAssignsId(45L);

        passService.recordPass("OP-14", new PassRequestDto("VAN_SUV", "bns 4417", "cash"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).publish(
                eq("PASS_RECORDED"), eq("OP-14"), eq(3), eq("PASS"), eq("45"), payload.capture());

        assertThat(payload.getValue())
                .containsEntry("plate", "BNS 4417")
                .containsEntry("vehicleClass", "VAN_SUV")
                .containsEntry("paymentMethod", "CASH")
                .containsEntry("amount", "4.50");
    }

    @Test
    @DisplayName("refuses an unknown vehicle class and saves nothing")
    void rejectsUnknownVehicleClass() {
        givenActiveShiftOnLane(3);
        when(vehicleClassRepository.findByCode("HOVERCRAFT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passService.recordPass("OP-14",
                new PassRequestDto("HOVERCRAFT", "KTR 8891", "CASH")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(passRepository, never()).save(any());
        verifyNoInteractions(auditClient);
    }

    @Test
    @DisplayName("refuses an unknown payment method and saves nothing")
    void rejectsUnknownPaymentMethod() {
        givenActiveShiftOnLane(3);
        givenVehicleClass("CAR", "Car", "3.00");

        assertThatThrownBy(() -> passService.recordPass("OP-14",
                new PassRequestDto("CAR", "KTR 8891", "BITCOIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BITCOIN");

        verify(passRepository, never()).save(any());
        verifyNoInteractions(auditClient);
    }

    @Test
    @DisplayName("refuses to open the gate for an operator with no active shift")
    void requiresAnActiveShift() {
        when(shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus("OP-99", ShiftStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passService.recordPass("OP-99",
                new PassRequestDto("CAR", "KTR 8891", "CASH")))
                .isInstanceOf(NoActiveShiftException.class);

        verify(passRepository, never()).save(any());
        verifyNoInteractions(auditClient, vehicleClassRepository);
    }

    @Test
    @DisplayName("asks for a bounded page of recent passes rather than the whole shift")
    void boundsTheRecentPassesQuery() {
        Shift shift = givenActiveShiftOnLane(3);
        shift.setId(11L);
        when(passRepository.findRecentByShift(eq(11L), any(Pageable.class))).thenReturn(List.of());

        passService.recentPasses("OP-14");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(passRepository).findRecentByShift(eq(11L), pageable.capture());
        // A busy lane accumulates hundreds of passes per shift; the console shows a tail.
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private Pass capturedPass() {
        ArgumentCaptor<Pass> pass = ArgumentCaptor.forClass(Pass.class);
        verify(passRepository).save(pass.capture());
        return pass.getValue();
    }

    private Shift givenActiveShiftOnLane(int laneNumber) {
        Lane lane = new Lane();
        lane.setLaneNumber(laneNumber);
        lane.setMode(LaneMode.MANNED);
        lane.setStatus(LaneStatus.OPEN);
        lane.setQueueLength(0);

        Shift shift = new Shift();
        shift.setLane(lane);
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.setStartsAt(OffsetDateTime.now().minusHours(2));
        shift.setEndsAt(OffsetDateTime.now().plusHours(6));

        when(shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus(anyString(), eq(ShiftStatus.ACTIVE)))
                .thenReturn(Optional.of(shift));
        return shift;
    }

    private void givenVehicleClass(String code, String label, String fare) {
        VehicleClass vehicleClass = new VehicleClass();
        vehicleClass.setCode(code);
        vehicleClass.setLabel(label);
        vehicleClass.setFare(new BigDecimal(fare));
        vehicleClass.setSortOrder(1);
        when(vehicleClassRepository.findByCode(code)).thenReturn(Optional.of(vehicleClass));
    }

    private void givenSaveAssignsId(long id) {
        when(passRepository.save(any(Pass.class))).thenAnswer(invocation -> {
            Pass pass = invocation.getArgument(0);
            pass.setId(id);
            return pass;
        });
    }
}
