package com.john.northgate.toll.service;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.config.JwtService;
import com.john.northgate.toll.dto.LoginRequestDto;
import com.john.northgate.toll.dto.LoginResponseDto;
import com.john.northgate.toll.entity.*;
import com.john.northgate.toll.exception.InvalidCredentialsException;
import com.john.northgate.toll.repository.ShiftRepository;
import com.john.northgate.toll.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final LoginRequestDto GOOD_LOGIN = new LoginRequestDto("op-14", "1234");

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditClient auditClient;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("issues a token and resolves the operator's lane from their active shift")
    void issuesTokenAndResolvesLane() {
        Staff operator = staff("OP-14", "R. Alvarez", Role.OPERATOR, true);
        when(staffRepository.findByStaffCodeIgnoreCase("op-14")).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("1234", operator.getPinHash())).thenReturn(true);
        when(shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus("OP-14", ShiftStatus.ACTIVE))
                .thenReturn(Optional.of(shiftOn(operator, 3)));
        when(jwtService.issue("OP-14", "R. Alvarez", "OPERATOR")).thenReturn("signed-token");

        LoginResponseDto response = authService.login(GOOD_LOGIN);

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.staffCode()).isEqualTo("OP-14");
        assertThat(response.fullName()).isEqualTo("R. Alvarez");
        assertThat(response.role()).isEqualTo("OPERATOR");
        assertThat(response.laneNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("reports no lane for a manager, who holds no shift")
    void reportsNoLaneWithoutAnActiveShift() {
        Staff manager = staff("MG-02", "D. Okafor", Role.MANAGER, true);
        when(staffRepository.findByStaffCodeIgnoreCase("mg-02")).thenReturn(Optional.of(manager));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus("MG-02", ShiftStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(jwtService.issue(anyString(), anyString(), anyString())).thenReturn("signed-token");

        LoginResponseDto response = authService.login(new LoginRequestDto("mg-02", "1234"));

        assertThat(response.role()).isEqualTo("MANAGER");
        assertThat(response.laneNumber()).isNull();
    }

    @Test
    @DisplayName("rejects a wrong PIN without issuing a token")
    void rejectsWrongPin() {
        Staff operator = staff("OP-14", "R. Alvarez", Role.OPERATOR, true);
        when(staffRepository.findByStaffCodeIgnoreCase("op-14")).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("9999", operator.getPinHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("op-14", "9999")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService, auditClient);
    }

    @Test
    @DisplayName("rejects an unknown staff code without checking a PIN")
    void rejectsUnknownStaff() {
        when(staffRepository.findByStaffCodeIgnoreCase("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("nobody", "1234")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder, jwtService, auditClient);
    }

    @Test
    @DisplayName("refuses a deactivated account even with the correct PIN")
    void rejectsDeactivatedStaff() {
        Staff retired = staff("OP-14", "R. Alvarez", Role.OPERATOR, false);
        when(staffRepository.findByStaffCodeIgnoreCase("op-14")).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> authService.login(GOOD_LOGIN))
                .isInstanceOf(InvalidCredentialsException.class);

        // The PIN is never even checked once the account is inactive.
        verifyNoInteractions(passwordEncoder, jwtService, auditClient);
    }

    @Test
    @DisplayName("gives the same message whether the staff code or the PIN was wrong")
    void doesNotRevealWhichCredentialWasWrong() {
        when(staffRepository.findByStaffCodeIgnoreCase("nobody")).thenReturn(Optional.empty());
        Staff operator = staff("OP-14", "R. Alvarez", Role.OPERATOR, true);
        when(staffRepository.findByStaffCodeIgnoreCase("op-14")).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("9999", operator.getPinHash())).thenReturn(false);

        String unknownStaff = messageFrom(new LoginRequestDto("nobody", "1234"));
        String wrongPin = messageFrom(new LoginRequestDto("op-14", "9999"));

        // Distinct messages would let an attacker enumerate valid staff IDs.
        assertThat(unknownStaff).isEqualTo(wrongPin);
    }

    @Test
    @DisplayName("records a SIGN_IN audit event for a successful login")
    void publishesSignInAuditEvent() {
        Staff operator = staff("OP-14", "R. Alvarez", Role.OPERATOR, true);
        operator.setId(7L);
        when(staffRepository.findByStaffCodeIgnoreCase("op-14")).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(shiftRepository.findByStaffStaffCodeIgnoreCaseAndStatus(anyString(), eq(ShiftStatus.ACTIVE)))
                .thenReturn(Optional.of(shiftOn(operator, 3)));
        when(jwtService.issue(anyString(), anyString(), anyString())).thenReturn("signed-token");

        authService.login(GOOD_LOGIN);

        verify(auditClient).publish(
                eq("SIGN_IN"), eq("OP-14"), eq(3), eq("STAFF"), eq("7"), anyMap());
    }

    private String messageFrom(LoginRequestDto request) {
        try {
            authService.login(request);
            throw new AssertionError("expected the login to be rejected");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }

    private static Staff staff(String code, String fullName, Role role, boolean active) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setFullName(fullName);
        staff.setRole(role);
        staff.setPinHash("$2y$10$hashed");
        staff.setActive(active);
        return staff;
    }

    private static Shift shiftOn(Staff staff, int laneNumber) {
        Lane lane = new Lane();
        lane.setLaneNumber(laneNumber);
        lane.setMode(LaneMode.MANNED);
        lane.setStatus(LaneStatus.OPEN);
        lane.setQueueLength(0);

        Shift shift = new Shift();
        shift.setStaff(staff);
        shift.setLane(lane);
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.setStartsAt(OffsetDateTime.now().minusHours(3));
        shift.setEndsAt(OffsetDateTime.now().plusHours(5));
        return shift;
    }
}
